#include "fastddsjava.h"

#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <mutex>
#include <unordered_map>

#if defined(__ANDROID__)
#include <android/log.h>
#endif

#include <fastdds/config.hpp>
#include <fastdds/dds/core/StackAllocatedSequence.hpp>
#include <fastdds/dds/core/policy/QosPolicies.hpp>
#include <fastdds/dds/domain/DomainParticipant.hpp>
#include <fastdds/dds/domain/DomainParticipantFactory.hpp>
#include <fastdds/dds/publisher/DataWriter.hpp>
#include <fastdds/dds/publisher/DataWriterListener.hpp>
#include <fastdds/dds/publisher/Publisher.hpp>
#include <fastdds/dds/publisher/qos/DataWriterQos.hpp>
#include <fastdds/dds/subscriber/DataReader.hpp>
#include <fastdds/dds/subscriber/DataReaderListener.hpp>
#include <fastdds/dds/subscriber/Subscriber.hpp>
#include <fastdds/dds/subscriber/qos/DataReaderQos.hpp>
#include <fastdds/dds/topic/TopicDataType.hpp>
#include <fastdds/dds/topic/TypeSupport.hpp>
#include <fastdds/rtps/common/InstanceHandle.hpp>
#include <fastdds/rtps/common/SerializedPayload.hpp>

namespace fdds = eprosima::fastdds::dds;
namespace frtps = eprosima::fastdds::rtps;

namespace
{
std::atomic<bool> g_process_exiting{false};

void mark_process_exiting()
{
   g_process_exiting.store(true, std::memory_order_seq_cst);
}

/**
 * Registers mark_process_exiting with std::atexit once. Called after a participant was created: exit handlers run in
 * reverse order of registration, so the handler then runs before the destructors of the Fast-DDS singletons created up to
 * that point (DomainParticipantFactory deletes every participant that is still alive in its destructor).
 */
void register_exit_handler()
{
   static std::once_flag once;
   std::call_once(once, []() { std::atexit(mark_process_exiting); });
}

void log_warning(const char* format, ...)
{
   va_list args;
   va_start(args, format);
#if defined(__ANDROID__)
   __android_log_vprint(ANDROID_LOG_WARN, "fastddsjava", format, args);
#else
   std::fputs("fastddsjava: ", stderr);
   std::vfprintf(stderr, format, args);
   std::fputc('\n', stderr);
   std::fflush(stderr);
#endif
   va_end(args);
}
} // namespace

/* ------------------------------------------------------------------------------------------------------------------
 * Topic data (opaque CDR blob) and its TopicDataType
 * ------------------------------------------------------------------------------------------------------------------ */

const uint8_t* fastddsjava_TopicDataWrapper::write_ptr() const
{
   return has_src_view ? src_ptr : data_vector.data();
}

uint32_t fastddsjava_TopicDataWrapper::write_len() const
{
   return has_src_view ? src_len : static_cast<uint32_t>(data_vector.size());
}

std::vector<uint8_t>& fastddsjava_TopicDataWrapper::read_storage()
{
   return fallback != nullptr ? *fallback : data_vector;
}

uint8_t* fastddsjava_TopicDataWrapper::data_ptr()
{
   return data_vector.data();
}

/**
 * TopicDataType of every jros2 topic. The payload is produced and consumed by Java, so this type only copies bytes.
 *
 * Contract with Fast-DDS 3.6 (DataWriterImpl::perform_create_new_change, ReadTakeCommand):
 * - serialize() never writes more than payload.max_size bytes and returns false if the payload is too small (the
 *   write then fails with RETCODE_ERROR). It sets payload.length and never modifies payload.max_size or payload.data,
 *   which belong to the payload pool. Under the PREALLOCATED memory policy the payload has a fixed size that ignores
 *   calculate_serialized_size().
 * - deserialize() never returns false: false would silently drop the sample from the reader history.
 * - max_serialized_type_size is never 0 (a writer created first would then crash in the payload pool); it is only the
 *   initial pool node size, payloads larger than it are handled by PREALLOCATED_WITH_REALLOC / DYNAMIC policies.
 * - is_bounded() / is_plain() are false: true would silently switch PREALLOCATED_WITH_REALLOC to fixed-size
 *   PREALLOCATED pools and enable data sharing / loans, none of which a variable-size blob supports.
 */
class fastddsjava_TopicDataWrapperType : public fdds::TopicDataType
{
public:
   /** Initial payload pool node size in bytes (writer pool; readers use this + 3). */
   static constexpr uint32_t INITIAL_PAYLOAD_SIZE = 64;

   fastddsjava_TopicDataWrapperType(const std::string& name, uint16_t encapsulation) : encapsulation_(encapsulation)
   {
      set_name(name.c_str());
      max_serialized_type_size = INITIAL_PAYLOAD_SIZE;
      is_compute_key_provided = false;
   }

   bool serialize(const void* const data_, frtps::SerializedPayload_t& payload,
                  fdds::DataRepresentationId_t data_representation) override
   {
      if (fdds::DataRepresentationId_t::XCDR_DATA_REPRESENTATION != data_representation || data_ == nullptr)
         return false;

      const auto* data = static_cast<const fastddsjava_TopicDataWrapper*>(data_);
      const uint32_t length = data->write_len();
      const uint8_t* source = data->write_ptr();
      if (length == 0 || source == nullptr || payload.data == nullptr || payload.max_size < length)
         return false;

      std::memcpy(payload.data, source, length);
      payload.length = length;
      // Metadata only (the header is part of the bytes): the representation identifier of the CDR header if present.
      payload.encapsulation = length >= 4 ? static_cast<uint16_t>((source[0] << 8) | source[1]) : encapsulation_;
      return true;
   }

   bool deserialize(frtps::SerializedPayload_t& payload, void* data_) override
   {
      auto* data = static_cast<fastddsjava_TopicDataWrapper*>(data_);
      const uint32_t length = payload.data != nullptr ? payload.length : 0;
      if (data->dst_ptr != nullptr && length <= data->dst_cap)
      {
         if (length > 0)
            std::memcpy(data->dst_ptr, payload.data, length);
         data->in_dst = true;
      }
      else
      {
         // assign() reuses the vector's capacity, so a steady-state reader does not allocate.
         data->read_storage().assign(payload.data, payload.data + length);
         data->in_dst = false;
      }
      data->length = length;
      return true;
   }

   uint32_t calculate_serialized_size(const void* const data_, fdds::DataRepresentationId_t data_representation) override
   {
      if (fdds::DataRepresentationId_t::XCDR_DATA_REPRESENTATION != data_representation || data_ == nullptr)
         return 0;
      return static_cast<const fastddsjava_TopicDataWrapper*>(data_)->write_len();
   }

   bool compute_key(frtps::SerializedPayload_t& payload, frtps::InstanceHandle_t& ihandle, bool force_md5 = false) override
   {
      // Keyless type (is_compute_key_provided == false): Fast-DDS never calls this.
      (void)payload;
      (void)ihandle;
      (void)force_md5;
      return true;
   }

   bool compute_key(const void* const data, frtps::InstanceHandle_t& ihandle, bool force_md5 = false) override
   {
      (void)data;
      (void)ihandle;
      (void)force_md5;
      return true;
   }

   void* create_data() override
   {
      return fastddsjava_topic_data_wrapper_type_create_data(this);
   }

   void delete_data(void* data) override
   {
      fastddsjava_topic_data_wrapper_type_delete_data(this, static_cast<fastddsjava_TopicDataWrapper*>(data));
   }

   bool is_bounded() const override
   {
      return false;
   }

   bool is_plain(fdds::DataRepresentationId_t) const override
   {
      return false;
   }

private:
   uint16_t encapsulation_;
};

/**
 * Owns the TypeSupport (a std::shared_ptr subclass with virtual methods but no virtual destructor). Wrapping it in a
 * final aggregate lets us delete it without -Wdelete-non-abstract-non-virtual-dtor and without calling the destructor
 * by hand.
 */
struct fastddsjava_TypeSupportHolder final
{
   explicit fastddsjava_TypeSupportHolder(fastddsjava_TopicDataWrapperType* type) : type_support(type)
   {
   }

   fdds::TypeSupport type_support;
};

/* ------------------------------------------------------------------------------------------------------------------
 * Listener callback gate and lifecycle registry
 * ------------------------------------------------------------------------------------------------------------------ */

namespace
{
class CallbackGate;

/** Intrusive per-thread stack of the gates whose callbacks are running on this thread (lives on the C++ stack). */
struct GateScopeLink
{
   const CallbackGate* gate;
   const GateScopeLink* previous;
};

thread_local const GateScopeLink* t_scope_top = nullptr;

/**
 * Counts running callbacks and refuses new ones once disabled, or once the process exits (fastddsjava_process_exiting).
 *
 * A callback increments in_flight before it reads enabled (both sequentially consistent). Therefore, once disable() has
 * returned and wait_idle() has observed in_flight == 0, no callback can still pass the gate. Callbacks that start after
 * disable() only touch the gate itself, never the Java callback.
 */
class CallbackGate
{
public:
   class Scope
   {
   public:
      explicit Scope(CallbackGate& gate) : gate_(gate), link_{&gate, t_scope_top}
      {
         gate_.in_flight_.fetch_add(1, std::memory_order_seq_cst);
         enabled_ = gate_.enabled_.load(std::memory_order_seq_cst) && !g_process_exiting.load(std::memory_order_seq_cst);
         t_scope_top = &link_;
      }

      ~Scope()
      {
         t_scope_top = link_.previous;
         gate_.leave();
      }

      Scope(const Scope&) = delete;
      Scope& operator=(const Scope&) = delete;

      explicit operator bool() const
      {
         return enabled_;
      }

   private:
      CallbackGate& gate_;
      GateScopeLink link_;
      bool enabled_ = false;
   };

   void enable()
   {
      enabled_.store(true, std::memory_order_seq_cst);
   }

   void disable()
   {
      enabled_.store(false, std::memory_order_seq_cst);
   }

   bool entered_by_current_thread() const
   {
      for (const GateScopeLink* link = t_scope_top; link != nullptr; link = link->previous)
      {
         if (link->gate == this)
            return true;
      }
      return false;
   }

   /**
    * Waits until no callback is running. The decision is always taken under mutex_, which leave() holds while it
    * decrements the counter for a waiter, so once this returns true no callback touches the gate any more and its
    * owner may be destroyed.
    *
    * @return false if a callback is still running after the timeout or if the calling thread is itself inside one
    */
   bool wait_idle(std::chrono::milliseconds timeout)
   {
      if (entered_by_current_thread())
         return false;

      waiters_.fetch_add(1, std::memory_order_seq_cst);
      bool idle = false;
      {
         std::unique_lock<std::mutex> lock(mutex_);
         const auto deadline = std::chrono::steady_clock::now() + timeout;
         // Short slices bound the delay of the (benign) race where a callback decrements without notifying.
         while (!(idle = in_flight_.load(std::memory_order_seq_cst) == 0) && std::chrono::steady_clock::now() < deadline)
            cv_.wait_for(lock, std::chrono::milliseconds(5));
      }
      waiters_.fetch_sub(1, std::memory_order_seq_cst);
      return idle;
   }

private:
   void leave()
   {
      // waiters_ is read while in_flight_ > 0, so no waiter can have concluded "idle" and freed the gate yet.
      if (waiters_.load(std::memory_order_seq_cst) > 0)
      {
         std::lock_guard<std::mutex> lock(mutex_);
         in_flight_.fetch_sub(1, std::memory_order_seq_cst);
         cv_.notify_all();
      }
      else
      {
         // Nothing touches the gate after this decrement.
         in_flight_.fetch_sub(1, std::memory_order_seq_cst);
      }
   }

   std::atomic<bool> enabled_{true};
   std::atomic<int> in_flight_{0};
   std::atomic<int> waiters_{0};
   std::mutex mutex_;
   std::condition_variable cv_;
};

using CallbackPtr = std::shared_ptr<const std::function<void()>>;

/**
 * Holds one callback. set() may race with invoke(): a running invocation keeps its own reference, so the previous
 * callback (and the JNI global reference it owns) is destroyed when the last user drops it, never while it runs.
 */
class CallbackSlot
{
public:
   void set(std::function<void()> callback)
   {
      CallbackPtr next = callback ? std::make_shared<const std::function<void()>>(std::move(callback)) : nullptr;
      CallbackPtr previous;
      {
         std::lock_guard<std::mutex> lock(mutex_);
         previous = std::move(slot_);
         slot_ = std::move(next);
      }
      // previous is released here, outside the lock.
   }

   void invoke() const
   {
      CallbackPtr callback;
      {
         std::lock_guard<std::mutex> lock(mutex_);
         callback = slot_;
      }
      if (callback && *callback)
         (*callback)();
   }

private:
   mutable std::mutex mutex_;
   CallbackPtr slot_;
};

constexpr std::chrono::milliseconds LISTENER_IDLE_TIMEOUT{2000};
} // namespace

/**
 * State shared by both listener kinds. The counters are guarded by the registry mutex.
 */
class fastddsjava_ListenerCore
{
public:
   virtual ~fastddsjava_ListenerCore() = default;

   virtual void clear_callbacks() = 0;

   CallbackGate gate;
   /** Number of entities this listener is currently installed on. */
   int attached = 0;
   /**
    * Number of live entities this listener was ever installed on. Only deleting the entity fences Fast-DDS callbacks
    * (set_listener does not), so the listener memory may only be released when this drops to 0.
    */
   int entity_refs = 0;
   bool delete_requested = false;
};

class fastddsjava_DataReaderListener : public fdds::DataReaderListener, public fastddsjava_ListenerCore
{
public:
   void set_on_data_available_callback(std::function<void()> callback)
   {
      on_data_callback.set(std::move(callback));
   }

   void set_on_subscription_callback(std::function<void()> callback)
   {
      on_subscription_callback.set(std::move(callback));
   }

   void on_data_available(fdds::DataReader* reader) override
   {
      (void)reader;
      CallbackGate::Scope scope(gate);
      if (scope)
         on_data_callback.invoke();
   }

   void on_subscription_matched(fdds::DataReader* reader, const fdds::SubscriptionMatchedStatus& info) override
   {
      (void)reader;
      (void)info;
      CallbackGate::Scope scope(gate);
      if (scope)
         on_subscription_callback.invoke();
   }

   void clear_callbacks() override
   {
      on_data_callback.set(nullptr);
      on_subscription_callback.set(nullptr);
   }

private:
   CallbackSlot on_data_callback;
   CallbackSlot on_subscription_callback;
};

class fastddsjava_DataWriterListener : public fdds::DataWriterListener, public fastddsjava_ListenerCore
{
public:
   void set_on_publication_callback(std::function<void()> callback)
   {
      on_publication_callback.set(std::move(callback));
   }

   void on_publication_matched(fdds::DataWriter* writer, const fdds::PublicationMatchedStatus& info) override
   {
      (void)writer;
      (void)info;
      CallbackGate::Scope scope(gate);
      if (scope)
         on_publication_callback.invoke();
   }

   void clear_callbacks() override
   {
      on_publication_callback.set(nullptr);
   }

private:
   CallbackSlot on_publication_callback;
};

namespace
{
/** Listeners an entity (DataReader / DataWriter) has used, keyed by the entity pointer. */
struct EntityListeners
{
   fastddsjava_ListenerCore* current = nullptr;
   std::vector<fastddsjava_ListenerCore*> history;
};

struct ListenerRegistry
{
   std::mutex mutex;
   std::unordered_map<const void*, EntityListeners> entities;
};

ListenerRegistry& registry()
{
   // Intentionally leaked: avoids static destruction order problems at process exit.
   static ListenerRegistry* instance = new ListenerRegistry();
   return *instance;
}

// All *_locked helpers require registry().mutex.

void attach_locked(EntityListeners& entry, fastddsjava_ListenerCore* listener)
{
   if (entry.current == listener)
      return;

   if (entry.current != nullptr)
   {
      fastddsjava_ListenerCore* previous = entry.current;
      if (--previous->attached <= 0)
      {
         previous->attached = 0;
         previous->gate.disable();
      }
   }

   if (listener != nullptr)
   {
      ++listener->attached;
      if (!listener->delete_requested)
         listener->gate.enable();
      if (std::find(entry.history.begin(), entry.history.end(), listener) == entry.history.end())
      {
         entry.history.push_back(listener);
         ++listener->entity_refs;
      }
   }

   entry.current = listener;
}

/** The entity was deleted successfully (a fence): drop its references and collect listeners that can now be freed. */
void entity_deleted_locked(EntityListeners& entry, std::vector<fastddsjava_ListenerCore*>& to_free)
{
   attach_locked(entry, nullptr);
   for (fastddsjava_ListenerCore* listener : entry.history)
   {
      if (--listener->entity_refs <= 0)
      {
         listener->entity_refs = 0;
         if (listener->delete_requested)
            to_free.push_back(listener);
      }
   }
   entry.history.clear();
}

void free_listener(fastddsjava_ListenerCore* listener)
{
   // Every entity it was installed on is deleted, so Fast-DDS holds no pointer to it any more. Only a callback that is
   // still unwinding (or a caller inside one of its callbacks) can touch it.
   if (listener->gate.wait_idle(LISTENER_IDLE_TIMEOUT))
      delete listener;
   else
      log_warning("a listener callback is still running; keeping the (disabled) listener %p alive", static_cast<void*>(listener));
}

void free_listeners(const std::vector<fastddsjava_ListenerCore*>& listeners)
{
   for (fastddsjava_ListenerCore* listener : listeners)
      free_listener(listener);
}

void delete_listener(fastddsjava_ListenerCore* listener)
{
   if (listener == nullptr)
      return;

   // Drops the Java callbacks (JNI global references) now; a callback that is still running keeps its own reference.
   // This happens before delete_requested is set: until then no other thread may free the listener.
   listener->clear_callbacks();

   bool free_now;
   {
      std::lock_guard<std::mutex> lock(registry().mutex);
      if (listener->delete_requested)
         return;
      listener->delete_requested = true;
      listener->gate.disable();
      free_now = listener->entity_refs <= 0;
   }

   // With entity_refs == 0 nothing can attach it again (delete_requested) or free it concurrently.
   if (free_now)
      free_listener(listener);
   // else: freed by the deletion of the last entity it was installed on; it must not be touched here any more.
}

/** False once the listener was deleted (it may still exist, disabled, until its entities are deleted). */
bool accepts_callbacks(fastddsjava_ListenerCore* listener)
{
   std::lock_guard<std::mutex> lock(registry().mutex);
   return !listener->delete_requested;
}

template <typename Entity, typename Listener>
uint32_t set_entity_listener(Entity* entity, Listener* listener)
{
   std::lock_guard<std::mutex> lock(registry().mutex);
   if (listener != nullptr && listener->delete_requested)
      return fdds::RETCODE_PRECONDITION_NOT_MET;

   // set_listener only swaps a pointer (no user callback runs inside), so it is safe to call under the registry mutex.
   fdds::ReturnCode_t ret = entity->set_listener(listener);
   if (fdds::RETCODE_OK == ret)
      attach_locked(registry().entities[entity], listener);
   return static_cast<uint32_t>(ret);
}

/** Deletes an entity with deleter (which may run other endpoints' callbacks, so no lock is held while it runs). */
template <typename Deleter>
uint32_t delete_entity(const void* entity, Deleter deleter)
{
   EntityListeners entry;
   bool tracked = false;
   {
      // Detach the entry first: once the entity is deleted its address may be reused by a new entity.
      std::lock_guard<std::mutex> lock(registry().mutex);
      auto it = registry().entities.find(entity);
      if (it != registry().entities.end())
      {
         entry = std::move(it->second);
         registry().entities.erase(it);
         tracked = true;
      }
   }

   fdds::ReturnCode_t ret = deleter();

   std::vector<fastddsjava_ListenerCore*> to_free;
   if (tracked)
   {
      std::lock_guard<std::mutex> lock(registry().mutex);
      if (fdds::RETCODE_OK == ret)
         entity_deleted_locked(entry, to_free);
      else
         registry().entities[entity] = std::move(entry);
   }
   free_listeners(to_free);
   return static_cast<uint32_t>(ret);
}

void use_xcdr1(fdds::DataRepresentationQosPolicy& representation)
{
   // jros2 payloads are plain CDR (XCDR1), as rmw_fastrtps announces. There is no XML element for this policy.
   representation.m_value.clear();
   representation.m_value.push_back(fdds::DataRepresentationId_t::XCDR_DATA_REPRESENTATION);
}

void copy_guid(const frtps::GUID_t& guid, uint8_t* guid_out)
{
   std::memcpy(guid_out, guid.guidPrefix.value, 12);
   std::memcpy(guid_out + 12, guid.entityId.value, 4);
}

/** One persistent SampleInfo sequence per thread, so take() does not allocate on every call. */
fdds::SampleInfoSeq& thread_sample_infos()
{
   thread_local fdds::SampleInfoSeq sample_infos(1);
   return sample_infos;
}

/**
 * Takes the next sample in any sample state (jros2 may have read it in the listener before) into data.
 * DataReader::take_next_sample cannot be used: it only returns NOT_READ samples.
 */
fdds::ReturnCode_t take_one_any_state(fdds::DataReader* reader, void* data, const fdds::SampleInfo** info_out)
{
   fdds::StackAllocatedSequence<void*, 1> data_values;
   const_cast<void**>(data_values.buffer())[0] = data;
   fdds::SampleInfoSeq& sample_infos = thread_sample_infos();
   sample_infos.length(0); // take() requires both sequences to have the same length, maximum and ownership

   fdds::ReturnCode_t ret = reader->take(data_values, sample_infos, 1, fdds::ANY_SAMPLE_STATE, fdds::ANY_VIEW_STATE,
                                         fdds::ANY_INSTANCE_STATE);
   *info_out = (fdds::RETCODE_OK == ret && sample_infos.length() > 0) ? &sample_infos[0] : nullptr;
   return ret;
}

int32_t take_error(fdds::ReturnCode_t ret)
{
   return -(FASTDDSJAVA_TAKE_ERROR_BASE + static_cast<int32_t>(ret));
}
} // namespace

/* ------------------------------------------------------------------------------------------------------------------
 * Library info
 * ------------------------------------------------------------------------------------------------------------------ */

int32_t fastddsjava_abi_version()
{
   return FASTDDSJAVA_ABI_VERSION;
}

bool fastddsjava_has_security()
{
   // HAVE_SECURITY comes from the fastdds/config.hpp of the Fast-DDS build this library is compiled and linked against.
#if HAVE_SECURITY
   return true;
#else
   return false;
#endif
}

bool fastddsjava_process_exiting()
{
   return g_process_exiting.load(std::memory_order_seq_cst);
}

/* ------------------------------------------------------------------------------------------------------------------
 * Entities
 * ------------------------------------------------------------------------------------------------------------------ */

uint32_t fastddsjava_load_xml_profiles_string(const std::string& xml)
{
   auto factory = fdds::DomainParticipantFactory::get_instance();
   return factory->load_XML_profiles_string(xml.c_str(), xml.length());
}

void* fastddsjava_create_participant(const std::string& profile_name)
{
   auto factory = fdds::DomainParticipantFactory::get_instance();
   void* participant = factory->create_participant_with_profile(profile_name);
   if (participant != nullptr)
      register_exit_handler(); // after the singletons this participant needed exist (see register_exit_handler)
   return participant;
}

uint32_t fastddsjava_delete_participant(void* participant_)
{
   if (participant_ == nullptr)
      return fdds::RETCODE_BAD_PARAMETER;
   auto* participant = static_cast<fdds::DomainParticipant*>(participant_);
   auto factory = fdds::DomainParticipantFactory::get_instance();
   return factory->delete_participant(participant);
}

void* fastddsjava_create_publisher(void* participant_, const std::string& profile_name)
{
   if (participant_ == nullptr)
      return nullptr;
   auto* participant = static_cast<fdds::DomainParticipant*>(participant_);
   return participant->create_publisher_with_profile(profile_name);
}

uint32_t fastddsjava_delete_publisher(void* participant_, void* publisher_)
{
   if (participant_ == nullptr || publisher_ == nullptr)
      return fdds::RETCODE_BAD_PARAMETER;
   auto* participant = static_cast<fdds::DomainParticipant*>(participant_);
   auto* publisher = static_cast<fdds::Publisher*>(publisher_);
   return participant->delete_publisher(publisher);
}

void* fastddsjava_create_subscriber(void* participant_, const std::string& profile_name)
{
   if (participant_ == nullptr)
      return nullptr;
   auto* participant = static_cast<fdds::DomainParticipant*>(participant_);
   return participant->create_subscriber_with_profile(profile_name);
}

uint32_t fastddsjava_delete_subscriber(void* participant_, void* subscriber_)
{
   if (participant_ == nullptr || subscriber_ == nullptr)
      return fdds::RETCODE_BAD_PARAMETER;
   auto* participant = static_cast<fdds::DomainParticipant*>(participant_);
   auto* subscriber = static_cast<fdds::Subscriber*>(subscriber_);
   return participant->delete_subscriber(subscriber);
}

fastddsjava_TopicDataWrapperType* fastddsjava_create_topic_data_wrapper_type(const std::string& name, uint16_t encapsulation)
{
   return new fastddsjava_TopicDataWrapperType(name, encapsulation);
}

fastddsjava_TypeSupportHolder* fastddsjava_create_typesupport(fastddsjava_TopicDataWrapperType* type)
{
   if (type == nullptr)
      return nullptr;
   // The TypeSupport (a shared_ptr) takes ownership of type.
   return new fastddsjava_TypeSupportHolder(type);
}

uint32_t fastddsjava_register_type(void* participant_, fastddsjava_TypeSupportHolder* type_support)
{
   if (participant_ == nullptr || type_support == nullptr)
      return fdds::RETCODE_BAD_PARAMETER;
   auto* participant = static_cast<fdds::DomainParticipant*>(participant_);
   return participant->register_type(type_support->type_support);
}

uint32_t fastddsjava_unregister_type(void* participant_, const std::string& type_name)
{
   if (participant_ == nullptr)
      return fdds::RETCODE_BAD_PARAMETER;
   auto* participant = static_cast<fdds::DomainParticipant*>(participant_);
   return participant->unregister_type(type_name);
}

void fastddsjava_delete_typesupport(fastddsjava_TypeSupportHolder* type_support)
{
   delete type_support;
}

std::string fastddsjava_topic_data_wrapper_type_get_name(fastddsjava_TopicDataWrapperType* type)
{
   return type != nullptr ? type->get_name() : std::string();
}

fastddsjava_TopicDataWrapper* fastddsjava_topic_data_wrapper_type_create_data(fastddsjava_TopicDataWrapperType* type)
{
   // Independent of the type object, so data can outlive it. One zero byte, as ABI v1 always did.
   (void)type;
   auto* data = new fastddsjava_TopicDataWrapper();
   data->data_vector.assign(1, 0);
   return data;
}

void fastddsjava_topic_data_wrapper_type_delete_data(fastddsjava_TopicDataWrapperType* type, fastddsjava_TopicDataWrapper* data)
{
   (void)type;
   delete data;
}

void* fastddsjava_create_topic(void* participant_, fastddsjava_TopicDataWrapperType* type, const std::string& topic_name,
                               const std::string& profile_name)
{
   if (participant_ == nullptr || type == nullptr)
      return nullptr;
   auto* participant = static_cast<fdds::DomainParticipant*>(participant_);
   return participant->create_topic_with_profile(topic_name, type->get_name(), profile_name);
}

uint32_t fastddsjava_delete_topic(void* participant_, void* topic_)
{
   if (participant_ == nullptr || topic_ == nullptr)
      return fdds::RETCODE_BAD_PARAMETER;
   auto* participant = static_cast<fdds::DomainParticipant*>(participant_);
   auto* topic = static_cast<fdds::Topic*>(topic_);
   return participant->delete_topic(topic);
}

void* fastddsjava_create_datawriter(void* publisher_, void* topic_, const std::string& profile_name)
{
   if (publisher_ == nullptr || topic_ == nullptr)
      return nullptr;
   auto* publisher = static_cast<fdds::Publisher*>(publisher_);
   auto* topic = static_cast<fdds::Topic*>(topic_);

   // Same QoS as create_datawriter_with_profile (default QoS + profile), plus the data representation.
   fdds::DataWriterQos qos;
   if (fdds::RETCODE_OK != publisher->get_datawriter_qos_from_profile(profile_name, qos))
   {
      log_warning("unknown data writer profile '%s'", profile_name.c_str());
      return nullptr;
   }
   use_xcdr1(qos.representation());
   return publisher->create_datawriter(topic, qos, nullptr, fdds::StatusMask::all());
}

uint32_t fastddsjava_delete_datawriter(void* publisher_, void* writer_)
{
   if (publisher_ == nullptr || writer_ == nullptr)
      return fdds::RETCODE_BAD_PARAMETER;
   auto* publisher = static_cast<fdds::Publisher*>(publisher_);
   auto* writer = static_cast<fdds::DataWriter*>(writer_);
   return delete_entity(writer_, [publisher, writer]() { return publisher->delete_datawriter(writer); });
}

uint32_t fastddsjava_datawriter_write(void* writer_, fastddsjava_TopicDataWrapper* data)
{
   if (writer_ == nullptr || data == nullptr)
      return fdds::RETCODE_BAD_PARAMETER;
   auto* writer = static_cast<fdds::DataWriter*>(writer_);
   return writer->write(data);
}

uint32_t fastddsjava_datawriter_write_view(void* writer_, const uint8_t* ptr, uint32_t len)
{
   if (writer_ == nullptr || ptr == nullptr || len == 0)
      return fdds::RETCODE_BAD_PARAMETER;

   // A stack wrapper per call: no allocation, no state shared between concurrent writers. DataWriter::write() only
   // uses the data synchronously (calculate_serialized_size + serialize), in SYNCHRONOUS and ASYNCHRONOUS publish mode.
   fastddsjava_TopicDataWrapper view;
   view.src_ptr = ptr;
   view.src_len = len;
   view.has_src_view = true;
   return static_cast<fdds::DataWriter*>(writer_)->write(&view);
}

uint32_t fastddsjava_datawriter_set_listener(void* writer_, fastddsjava_DataWriterListener* listener)
{
   if (writer_ == nullptr)
      return fdds::RETCODE_BAD_PARAMETER;
   return set_entity_listener(static_cast<fdds::DataWriter*>(writer_), listener);
}

uint32_t fastddsjava_datawriter_get_publication_matched_status(void* writer_, fdds::PublicationMatchedStatus& status)
{
   if (writer_ == nullptr)
      return fdds::RETCODE_BAD_PARAMETER;
   auto* writer = static_cast<fdds::DataWriter*>(writer_);
   return writer->get_publication_matched_status(status);
}

void* fastddsjava_create_datareader(void* subscriber_, void* topic_, fastddsjava_DataReaderListener* listener,
                                    const std::string& profile_name)
{
   if (subscriber_ == nullptr || topic_ == nullptr)
      return nullptr;
   auto* subscriber = static_cast<fdds::Subscriber*>(subscriber_);
   auto* topic = static_cast<fdds::Topic*>(topic_);

   // Same QoS as create_datareader_with_profile (default QoS + profile), plus the data representation.
   fdds::DataReaderQos qos;
   if (fdds::RETCODE_OK != subscriber->get_datareader_qos_from_profile(profile_name, qos))
   {
      log_warning("unknown data reader profile '%s'", profile_name.c_str());
      return nullptr;
   }
   use_xcdr1(qos.representation());

   if (listener != nullptr)
   {
      std::lock_guard<std::mutex> lock(registry().mutex);
      if (listener->delete_requested)
         return nullptr;
      // Count the attachment before creating: matching may already invoke the listener inside create_datareader.
      ++listener->attached;
      ++listener->entity_refs;
      listener->gate.enable();
   }

   fdds::DataReader* reader = subscriber->create_datareader(topic, qos, listener, fdds::StatusMask::all());

   if (listener != nullptr)
   {
      std::vector<fastddsjava_ListenerCore*> to_free;
      {
         std::lock_guard<std::mutex> lock(registry().mutex);
         EntityListeners registered;
         registered.current = listener;
         registered.history.push_back(listener);
         if (reader != nullptr)
            registry().entities[reader] = std::move(registered);
         else
            entity_deleted_locked(registered, to_free); // undo the pre-registration
      }
      free_listeners(to_free);
   }
   return reader;
}

uint32_t fastddsjava_delete_datareader(void* subscriber_, void* reader_)
{
   if (subscriber_ == nullptr || reader_ == nullptr)
      return fdds::RETCODE_BAD_PARAMETER;
   auto* subscriber = static_cast<fdds::Subscriber*>(subscriber_);
   auto* reader = static_cast<fdds::DataReader*>(reader_);
   return delete_entity(reader_, [subscriber, reader]() { return subscriber->delete_datareader(reader); });
}

uint32_t fastddsjava_datareader_read_next_sample(void* reader_, void* data, fdds::SampleInfo* info)
{
   if (reader_ == nullptr || data == nullptr || info == nullptr)
      return fdds::RETCODE_BAD_PARAMETER;
   // Topic data never carries a borrowed destination, so the payload lands in its data_vector.
   auto* reader = static_cast<fdds::DataReader*>(reader_);
   return reader->read_next_sample(data, info);
}

uint32_t fastddsjava_datareader_take_next_custom(void* reader_, void* data, fdds::SampleInfo* info)
{
   if (reader_ == nullptr || data == nullptr)
      return fdds::RETCODE_BAD_PARAMETER;

   // ABI v1 semantics: the payload of a valid sample lands in the topic data's data_vector.
   const fdds::SampleInfo* taken = nullptr;
   fdds::ReturnCode_t ret = take_one_any_state(static_cast<fdds::DataReader*>(reader_), data, &taken);
   if (fdds::RETCODE_OK == ret && info != nullptr && taken != nullptr)
      *info = *taken;
   return static_cast<uint32_t>(ret);
}

int32_t fastddsjava_datareader_take_next_to_buffer(void* reader_, fastddsjava_TopicDataWrapper* data, fdds::SampleInfo* info,
                                                   uint8_t* dst, uint32_t dst_cap)
{
   if (reader_ == nullptr || data == nullptr)
      return take_error(fdds::RETCODE_BAD_PARAMETER);

   // The borrowed destination lives in a wrapper on this stack frame only (an empty vector does not allocate); a
   // payload that does not fit goes to the caller's topic data.
   fastddsjava_TopicDataWrapper view;
   view.dst_ptr = dst;
   view.dst_cap = dst != nullptr ? dst_cap : 0;
   view.fallback = &data->data_vector;

   const fdds::SampleInfo* taken = nullptr;
   fdds::ReturnCode_t ret = take_one_any_state(static_cast<fdds::DataReader*>(reader_), &view, &taken);

   if (fdds::RETCODE_NO_DATA == ret)
      return FASTDDSJAVA_TAKE_NO_DATA;
   if (fdds::RETCODE_OK != ret)
      return take_error(ret);
   if (taken == nullptr)
      return take_error(fdds::RETCODE_ERROR);

   if (info != nullptr)
      *info = *taken;
   if (!taken->valid_data)
      return FASTDDSJAVA_TAKE_INVALID_SAMPLE;
   if (view.length > static_cast<uint32_t>(INT32_MAX))
      return take_error(fdds::RETCODE_OUT_OF_RESOURCES);
   return static_cast<int32_t>(view.length);
}

uint32_t fastddsjava_datareader_set_listener(void* reader_, fastddsjava_DataReaderListener* listener)
{
   if (reader_ == nullptr)
      return fdds::RETCODE_BAD_PARAMETER;
   return set_entity_listener(static_cast<fdds::DataReader*>(reader_), listener);
}

uint32_t fastddsjava_datareader_get_unread_count(void* reader_)
{
   if (reader_ == nullptr)
      return 0;
   auto* reader = static_cast<fdds::DataReader*>(reader_);
   return reader->get_unread_count();
}

uint32_t fastddsjava_datareader_get_subscription_matched_status(void* reader_, fdds::SubscriptionMatchedStatus& status)
{
   if (reader_ == nullptr)
      return fdds::RETCODE_BAD_PARAMETER;
   auto* reader = static_cast<fdds::DataReader*>(reader_);
   return reader->get_subscription_matched_status(status);
}

void fastddsjava_get_participant_guid(void* participant_, uint8_t* guid_out)
{
   if (participant_ == nullptr)
   {
      std::memset(guid_out, 0, 16);
      return;
   }
   copy_guid(static_cast<fdds::DomainParticipant*>(participant_)->guid(), guid_out);
}

void fastddsjava_get_writer_guid(void* writer_, uint8_t* guid_out)
{
   if (writer_ == nullptr)
   {
      std::memset(guid_out, 0, 16);
      return;
   }
   copy_guid(static_cast<fdds::DataWriter*>(writer_)->guid(), guid_out);
}

void fastddsjava_get_reader_guid(void* reader_, uint8_t* guid_out)
{
   if (reader_ == nullptr)
   {
      std::memset(guid_out, 0, 16);
      return;
   }
   copy_guid(static_cast<fdds::DataReader*>(reader_)->guid(), guid_out);
}

void fastddsjava_subscription_matched_status_last_publication_guid(const fdds::SubscriptionMatchedStatus& status, uint8_t* guid_out)
{
   frtps::GUID_t guid;
   frtps::iHandle2GUID(guid, status.last_publication_handle);
   copy_guid(guid, guid_out);
}

/* ------------------------------------------------------------------------------------------------------------------
 * Topic data access
 * ------------------------------------------------------------------------------------------------------------------ */

void fastddsjava_topic_data_resize(fastddsjava_TopicDataWrapper* data, size_t size)
{
   if (data != nullptr)
      data->data_vector.resize(size);
}

size_t fastddsjava_topic_data_size(fastddsjava_TopicDataWrapper* data)
{
   return data != nullptr ? data->data_vector.size() : 0;
}

void fastddsjava_topic_data_write(fastddsjava_TopicDataWrapper* data, const uint8_t* src, size_t length)
{
   if (data == nullptr || src == nullptr || length == 0)
      return;
   if (data->data_vector.size() < length)
      data->data_vector.resize(length);
   std::memcpy(data->data_vector.data(), src, length);
}

void fastddsjava_topic_data_read(fastddsjava_TopicDataWrapper* data, uint8_t* dst, size_t length)
{
   if (data == nullptr || dst == nullptr)
      return;
   size_t n = std::min(length, data->data_vector.size());
   if (n > 0)
      std::memcpy(dst, data->data_vector.data(), n);
}

fdds::SampleInfo* fastddsjava_create_sample_info()
{
   return new fdds::SampleInfo();
}

void fastddsjava_delete_sample_info(fdds::SampleInfo* info)
{
   delete info;
}

/* ------------------------------------------------------------------------------------------------------------------
 * Listeners
 * ------------------------------------------------------------------------------------------------------------------ */

fastddsjava_DataReaderListener* fastddsjava_create_datareader_listener()
{
   return new fastddsjava_DataReaderListener();
}

void fastddsjava_delete_datareader_listener(fastddsjava_DataReaderListener* listener)
{
   delete_listener(listener);
}

void fastddsjava_datareader_listener_set_on_data_available(fastddsjava_DataReaderListener* listener, std::function<void()> callback)
{
   if (listener != nullptr && accepts_callbacks(listener))
      listener->set_on_data_available_callback(std::move(callback));
}

void fastddsjava_datareader_listener_set_on_subscription_matched(fastddsjava_DataReaderListener* listener, std::function<void()> callback)
{
   if (listener != nullptr && accepts_callbacks(listener))
      listener->set_on_subscription_callback(std::move(callback));
}

fastddsjava_DataWriterListener* fastddsjava_create_datawriter_listener()
{
   return new fastddsjava_DataWriterListener();
}

void fastddsjava_delete_datawriter_listener(fastddsjava_DataWriterListener* listener)
{
   delete_listener(listener);
}

void fastddsjava_datawriter_listener_set_on_publication_matched(fastddsjava_DataWriterListener* listener, std::function<void()> callback)
{
   if (listener != nullptr && accepts_callbacks(listener))
      listener->set_on_publication_callback(std::move(callback));
}
