/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/*
 * alloccount: an LD_PRELOAD shim that counts native heap calls per thread while "armed", for the jros2 native allocation
 * gate (us.ihmc.jros2.allocation.nativeheap). Linux/glibc only.
 *
 * Build:  cc -shared -fPIC -O2 -o liballoccount.so alloccount.c -ldl -lpthread
 * Use:    ALLOCCOUNT_FILE=/path/to/counts.bin LD_PRELOAD=/path/to/liballoccount.so java ...
 *
 * Interposed: malloc, calloc, realloc, reallocarray, free, posix_memalign, aligned_alloc, memalign, valloc, pvalloc, the
 * C++ operator new / new[] (plain, nothrow and aligned forms), and mmap / munmap / mremap called directly by a library
 * (glibc's malloc maps memory through internal calls that are not interposed, so those are counted as the malloc that
 * caused them). libstdc++'s operator new calls malloc (aligned forms: posix_memalign / aligned_alloc) through the PLT, so
 * it would be counted without its own wrapper too; the wrappers make the result independent of the C++ runtime and name
 * the cause: each operator new call is counted once, as "new", and the malloc it makes internally is not counted again.
 *
 * Control and results live in a memory-mapped file (path in ALLOCCOUNT_FILE), created by the shim at load time and
 * mapped by the Java test with a MappedByteBuffer, so no JNI is needed. Layout (little endian, offsets in bytes):
 *
 * Header (HEADER_SIZE bytes):
 *   0  u64 magic "ALLOCCN2"         8  u32 version          12 u32 armed (written by Java: 1 = count, 0 = off)
 *   16 u32 generation (Java increments it before arming; threads then claim fresh slots)
 *   20 u32 max_threads   24 u32 max_stacks   28 u32 max_depth   32 u32 thread_count (slots claimed)
 *   36 u32 dropped_threads (threads that found no free slot)    40 u32 slot_size   44 u32 header_size   48 u32 pid
 *   52 u32 ready (1 once the shim is initialized)   56 u64 unslotted_calls (calls of threads without a slot)
 *   64 u64 total_calls (all counted calls while armed, every thread)
 *   72 u64 probe_counter (incremented by alloccount_probe(), see below)
 *   80 u64 internal_calls (heap calls the shim itself caused while recording, e.g. inside backtrace(); must stay 0)
 *   88 u32 record_size
 *
 * Slot (one per thread and generation, slot_size bytes, starting at header_size + index * slot_size):
 *   0  u32 tid   4 u32 generation   8 char name[16] (pthread name when the slot was claimed)
 *   24 u64 counts[9]: malloc, calloc, realloc, memalign (posix_memalign, aligned_alloc, memalign, valloc, pvalloc),
 *      free (non-null pointers), bytes requested, new (C++ operator new / new[]), mmap (mmap / mremap), munmap
 *   96 u32 alloc_stack_count   100 u32 free_stack_count   104 u64 alloc_unrecorded   112 u64 free_unrecorded
 *   then max_stacks allocation stack records, then max_stacks free stack records (SLOT_HEADER_SIZE onwards).
 *   Stack record (record_size bytes): u32 kind, u32 depth, u64 size (of the first call), u64 count (calls with this exact
 *   stack and kind), u64 hash, u64 frames[max_depth] (raw return addresses, frame 0 = the caller of the interposed
 *   function). Distinct stacks are recorded once each; calls whose stack is new once the table is full are counted in
 *   *_unrecorded.
 *
 * Constraints honored by the counting path: it never allocates (dlsym bootstrap uses a static buffer, slots and stack
 * records are preallocated in the mapped file, the thread state is initial-exec TLS, the thread name is read with
 * prctl(PR_GET_NAME) and backtrace() is primed in the constructor so its lazy libgcc_s load happens before any
 * measurement; internal_calls proves it), it is re-entrancy safe (a per-thread guard), and it is lock free.
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <execinfo.h>
#include <fcntl.h>
#include <malloc.h>
#include <stdarg.h>
#include <stdatomic.h>
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

#define ALLOCCOUNT_MAGIC 0x324E43434F4C4C41ULL /* "ALLOCCN2" little endian */
#define ALLOCCOUNT_VERSION 2u
#define HEADER_SIZE 4096u
#define SLOT_HEADER_SIZE 128u
#define RECORD_HEADER_SIZE 32u
#define COUNT_KINDS 9

enum
{
   KIND_MALLOC = 1,
   KIND_CALLOC = 2,
   KIND_REALLOC = 3,
   KIND_MEMALIGN = 4,
   KIND_FREE = 5,
   KIND_NEW = 6,
   KIND_MMAP = 7,
   KIND_MUNMAP = 8
};

/* Index into slot.counts for each kind (5 is the byte total). */
static const int COUNT_INDEX[] = {-1, 0, 1, 2, 3, 4, 6, 7, 8};

struct header
{
   uint64_t magic;
   uint32_t version;
   _Atomic uint32_t armed;
   _Atomic uint32_t generation;
   uint32_t max_threads;
   uint32_t max_stacks;
   uint32_t max_depth;
   _Atomic uint32_t thread_count;
   _Atomic uint32_t dropped_threads;
   uint32_t slot_size;
   uint32_t header_size;
   uint32_t pid;
   _Atomic uint32_t ready;
   _Atomic uint64_t unslotted_calls;
   _Atomic uint64_t total_calls;
   _Atomic uint64_t probe_counter;
   _Atomic uint64_t internal_calls;
   uint32_t record_size;
};

struct slot
{
   uint32_t tid;
   uint32_t generation;
   char name[16];
   uint64_t counts[COUNT_KINDS];
   uint32_t alloc_stack_count;
   uint32_t free_stack_count;
   uint64_t alloc_unrecorded;
   uint64_t free_unrecorded;
};

struct record
{
   uint32_t kind;
   uint32_t depth;
   uint64_t size;
   uint64_t count;
   uint64_t hash;
   uint64_t frames[];
};

_Static_assert(sizeof(struct header) <= HEADER_SIZE, "header too large");
_Static_assert(offsetof(struct header, unslotted_calls) == 56, "header layout");
_Static_assert(offsetof(struct header, probe_counter) == 72, "header layout");
_Static_assert(offsetof(struct header, internal_calls) == 80, "header layout");
_Static_assert(offsetof(struct header, record_size) == 88, "header layout");
_Static_assert(offsetof(struct slot, counts) == 24, "slot layout");
_Static_assert(offsetof(struct slot, alloc_stack_count) == 96, "slot layout");
_Static_assert(offsetof(struct slot, alloc_unrecorded) == 104, "slot layout");
_Static_assert(sizeof(struct slot) <= SLOT_HEADER_SIZE, "slot header too large");
_Static_assert(sizeof(struct record) == RECORD_HEADER_SIZE, "record layout");

typedef void* (*malloc_fn)(size_t);
typedef void* (*calloc_fn)(size_t, size_t);
typedef void* (*realloc_fn)(void*, size_t);
typedef void (*free_fn)(void*);
typedef int (*posix_memalign_fn)(void**, size_t, size_t);
typedef void* (*aligned_alloc_fn)(size_t, size_t);
typedef void* (*valloc_fn)(size_t);
typedef void* (*mmap_fn)(void*, size_t, int, int, int, off_t);
typedef int (*munmap_fn)(void*, size_t);
typedef void* (*mremap_fn)(void*, size_t, size_t, int, ...);

static malloc_fn real_malloc;
static calloc_fn real_calloc;
static realloc_fn real_realloc;
static free_fn real_free;
static posix_memalign_fn real_posix_memalign;
static aligned_alloc_fn real_aligned_alloc;
static aligned_alloc_fn real_memalign;
static valloc_fn real_valloc;
static valloc_fn real_pvalloc;
static mmap_fn real_mmap;
static munmap_fn real_munmap;
static mremap_fn real_mremap;

/* Serves allocations made while dlsym resolves the real functions (dlsym may calloc its error buffer). */
static _Alignas(64) char bootstrap_buffer[65536];
static _Atomic size_t bootstrap_used;
static _Atomic int resolving;

static struct header* g_header;
static char* g_base;
static uint32_t g_slot_size;
static uint32_t g_record_size;
static uint32_t g_max_threads;
static uint32_t g_max_stacks;
static uint32_t g_max_depth;

/* t_in_hook: 0 = outside, HOOK_RECORDING = inside record() (heap calls are the shim's own: internal_calls),
   HOOK_NEW = inside an operator new wrapper (its inner malloc is part of the counted "new"). */
#define HOOK_RECORDING 1
#define HOOK_NEW 2
#define TLS __attribute__((tls_model("initial-exec")))
static __thread int t_in_hook TLS;
static __thread uint32_t t_generation TLS;
static __thread struct slot* t_slot TLS;
static __thread int t_has_slot TLS;

static int in_bootstrap(const void* p)
{
   return (const char*) p >= bootstrap_buffer && (const char*) p < bootstrap_buffer + sizeof(bootstrap_buffer);
}

static void* bootstrap_alloc(size_t size, size_t alignment)
{
   if (alignment < 16)
      alignment = 16;
   size_t used = atomic_load(&bootstrap_used);
   for (;;)
   {
      size_t start = (used + alignment - 1) & ~(alignment - 1);
      if (start + size > sizeof(bootstrap_buffer))
         return NULL;
      if (atomic_compare_exchange_weak(&bootstrap_used, &used, start + size))
         return bootstrap_buffer + start;
   }
}

static void resolve(void)
{
   if (real_free != NULL)
      return;
   atomic_store(&resolving, 1);
   real_malloc = (malloc_fn) dlsym(RTLD_NEXT, "malloc");
   real_calloc = (calloc_fn) dlsym(RTLD_NEXT, "calloc");
   real_realloc = (realloc_fn) dlsym(RTLD_NEXT, "realloc");
   real_posix_memalign = (posix_memalign_fn) dlsym(RTLD_NEXT, "posix_memalign");
   real_aligned_alloc = (aligned_alloc_fn) dlsym(RTLD_NEXT, "aligned_alloc");
   real_memalign = (aligned_alloc_fn) dlsym(RTLD_NEXT, "memalign");
   real_valloc = (valloc_fn) dlsym(RTLD_NEXT, "valloc");
   real_pvalloc = (valloc_fn) dlsym(RTLD_NEXT, "pvalloc");
   real_mmap = (mmap_fn) dlsym(RTLD_NEXT, "mmap");
   real_munmap = (munmap_fn) dlsym(RTLD_NEXT, "munmap");
   real_mremap = (mremap_fn) dlsym(RTLD_NEXT, "mremap");
   free_fn f = (free_fn) dlsym(RTLD_NEXT, "free");
   atomic_store(&resolving, 0);
   real_free = f; /* last: marks the table complete */
}

static struct slot* current_slot(void)
{
   uint32_t generation = atomic_load_explicit(&g_header->generation, memory_order_acquire);
   if (t_generation == generation && (t_has_slot || t_slot == NULL))
      return t_slot;

   t_generation = generation;
   t_has_slot = 0;
   t_slot = NULL;
   uint32_t index = atomic_fetch_add(&g_header->thread_count, 1);
   if (index >= g_max_threads)
   {
      atomic_fetch_add(&g_header->dropped_threads, 1);
      return NULL;
   }
   struct slot* slot = (struct slot*) (g_base + HEADER_SIZE + (size_t) index * g_slot_size);
   memset(slot, 0, sizeof(struct slot));
   slot->tid = (uint32_t) syscall(SYS_gettid);
   prctl(PR_GET_NAME, (unsigned long) slot->name, 0, 0, 0);
   slot->name[15] = 0;
   slot->generation = generation;
   t_slot = slot;
   t_has_slot = 1;
   return slot;
}

static uint64_t hash_frames(int kind, void* const* frames, int depth)
{
   uint64_t hash = 1469598103934665603ULL ^ (uint64_t) kind;
   for (int i = 0; i < depth; ++i)
   {
      hash ^= (uint64_t) (uintptr_t) frames[i];
      hash *= 1099511628211ULL;
   }
   return hash;
}

/* Adds the stack to the slot's table (once per distinct stack and kind, with a count). */
static void record_stack(struct slot* slot, int kind, size_t size)
{
   int is_free = kind == KIND_FREE || kind == KIND_MUNMAP;
   uint32_t* stack_count = is_free ? &slot->free_stack_count : &slot->alloc_stack_count;
   uint64_t* unrecorded = is_free ? &slot->free_unrecorded : &slot->alloc_unrecorded;
   if (g_max_stacks == 0)
   {
      (*unrecorded)++;
      return;
   }

   void* frames[258];
   int max_depth = g_max_depth > 254 ? 254 : (int) g_max_depth;
   /* +2: drop the frames of record_stack's callers inside the shim (record and the interposed function). */
   int depth = backtrace(frames, max_depth + 2);
   int skip = depth >= 2 ? 2 : depth;
   int kept = depth - skip;
   uint64_t hash = hash_frames(kind, frames + skip, kept);

   char* table = (char*) slot + SLOT_HEADER_SIZE + (is_free ? (size_t) g_max_stacks * g_record_size : 0);
   for (uint32_t i = 0; i < *stack_count; ++i)
   {
      struct record* existing = (struct record*) (table + (size_t) i * g_record_size);
      if (existing->hash == hash && existing->kind == (uint32_t) kind && existing->depth == (uint32_t) kept
          && memcmp(existing->frames, frames + skip, sizeof(uint64_t) * (size_t) kept) == 0)
      {
         existing->count++;
         return;
      }
   }
   if (*stack_count >= g_max_stacks)
   {
      (*unrecorded)++;
      return;
   }
   struct record* rec = (struct record*) (table + (size_t) (*stack_count) * g_record_size);
   rec->kind = (uint32_t) kind;
   rec->depth = (uint32_t) kept;
   rec->size = (uint64_t) size;
   rec->count = 1;
   rec->hash = hash;
   for (int i = 0; i < kept; ++i)
      rec->frames[i] = (uint64_t) (uintptr_t) frames[skip + i];
   (*stack_count)++;
}

__attribute__((noinline)) static void record(int kind, size_t size)
{
   if (g_header == NULL)
      return;
   if (!atomic_load_explicit(&g_header->armed, memory_order_relaxed))
      return;
   if (t_in_hook != 0)
   {
      if (t_in_hook == HOOK_RECORDING)
         atomic_fetch_add_explicit(&g_header->internal_calls, 1, memory_order_relaxed);
      return;
   }

   t_in_hook = HOOK_RECORDING;
   int saved_errno = errno;
   atomic_fetch_add_explicit(&g_header->total_calls, 1, memory_order_relaxed);
   struct slot* slot = current_slot();
   if (slot == NULL)
   {
      atomic_fetch_add_explicit(&g_header->unslotted_calls, 1, memory_order_relaxed);
   }
   else
   {
      slot->counts[COUNT_INDEX[kind]]++;
      if (kind != KIND_FREE && kind != KIND_MUNMAP)
         slot->counts[5] += size;
      record_stack(slot, kind, size);
   }
   errno = saved_errno;
   t_in_hook = 0;
}

static uint32_t env_u32(const char* name, uint32_t fallback, uint32_t min, uint32_t max)
{
   const char* value = getenv(name);
   if (value == NULL || *value == 0)
      return fallback;
   unsigned long parsed = strtoul(value, NULL, 10);
   if (parsed < min)
      return min;
   if (parsed > max)
      return max;
   return (uint32_t) parsed;
}

__attribute__((constructor)) static void alloccount_init(void)
{
   resolve();

   /* Prime backtrace(): its first call loads libgcc_s (dlopen + malloc). */
   void* frames[4];
   t_in_hook = HOOK_NEW;
   backtrace(frames, 4);
   t_in_hook = 0;

   const char* path = getenv("ALLOCCOUNT_FILE");
   if (path == NULL || *path == 0)
      return;

   g_max_threads = env_u32("ALLOCCOUNT_MAX_THREADS", 512, 1, 65536);
   g_max_stacks = env_u32("ALLOCCOUNT_MAX_STACKS", 32, 0, 4096);
   g_max_depth = env_u32("ALLOCCOUNT_MAX_DEPTH", 48, 1, 254);
   g_record_size = RECORD_HEADER_SIZE + 8u * g_max_depth;
   size_t slot_size = SLOT_HEADER_SIZE + 2 * (size_t) g_max_stacks * g_record_size;
   slot_size = (slot_size + 63) & ~(size_t) 63;
   g_slot_size = (uint32_t) slot_size;
   size_t total = HEADER_SIZE + (size_t) g_max_threads * slot_size;

   int fd = open(path, O_RDWR | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
   if (fd < 0)
      return;
   if (ftruncate(fd, (off_t) total) != 0)
   {
      close(fd);
      return;
   }
   void* mapped = real_mmap(NULL, total, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
   close(fd);
   if (mapped == MAP_FAILED)
      return;

   /* Only the process that created the file counts: children (e.g. a shell started by the JVM) inherit LD_PRELOAD but
      must not write into it. */
   unsetenv("ALLOCCOUNT_FILE");

   struct header* header = (struct header*) mapped;
   header->magic = ALLOCCOUNT_MAGIC;
   header->version = ALLOCCOUNT_VERSION;
   header->max_threads = g_max_threads;
   header->max_stacks = g_max_stacks;
   header->max_depth = g_max_depth;
   header->slot_size = g_slot_size;
   header->header_size = HEADER_SIZE;
   header->record_size = g_record_size;
   header->pid = (uint32_t) getpid();
   g_base = (char*) mapped;
   g_header = header;
   atomic_store(&header->ready, 1);
}

/* Lets a test verify the shim from native code without JNI: not used by the interposed functions. */
void alloccount_probe(void)
{
   if (g_header != NULL)
      atomic_fetch_add(&g_header->probe_counter, 1);
}

/* ---------------------------------------------------------------------------------------------------------------- */

void* malloc(size_t size)
{
   if (real_malloc == NULL)
   {
      if (atomic_load(&resolving))
         return bootstrap_alloc(size, 16);
      resolve();
   }
   void* p = real_malloc(size);
   record(KIND_MALLOC, size);
   return p;
}

void* calloc(size_t count, size_t size)
{
   if (real_calloc == NULL)
   {
      if (atomic_load(&resolving))
      {
         if (size != 0 && count > SIZE_MAX / size)
            return NULL;
         return bootstrap_alloc(count * size, 16); /* static storage is zeroed */
      }
      resolve();
   }
   void* p = real_calloc(count, size);
   record(KIND_CALLOC, count * size);
   return p;
}

void* realloc(void* ptr, size_t size)
{
   if (in_bootstrap(ptr))
   {
      /* Never freed; copy into a real allocation. The old size is unknown, but bounded by the buffer. */
      void* p = malloc(size);
      if (p != NULL)
      {
         size_t available = (size_t) (bootstrap_buffer + sizeof(bootstrap_buffer) - (char*) ptr);
         memcpy(p, ptr, size < available ? size : available);
      }
      return p;
   }
   if (real_realloc == NULL)
   {
      if (atomic_load(&resolving))
         return bootstrap_alloc(size, 16);
      resolve();
   }
   void* p = real_realloc(ptr, size);
   record(ptr == NULL ? KIND_MALLOC : (size == 0 ? KIND_FREE : KIND_REALLOC), size);
   return p;
}

void* reallocarray(void* ptr, size_t count, size_t size)
{
   if (size != 0 && count > SIZE_MAX / size)
   {
      errno = ENOMEM;
      return NULL;
   }
   return realloc(ptr, count * size);
}

void free(void* ptr)
{
   if (ptr == NULL || in_bootstrap(ptr))
      return;
   if (real_free == NULL)
      resolve();
   record(KIND_FREE, 0);
   real_free(ptr);
}

int posix_memalign(void** out, size_t alignment, size_t size)
{
   if (real_posix_memalign == NULL)
   {
      if (atomic_load(&resolving))
      {
         *out = bootstrap_alloc(size, alignment);
         return *out != NULL ? 0 : ENOMEM;
      }
      resolve();
   }
   int ret = real_posix_memalign(out, alignment, size);
   record(KIND_MEMALIGN, size);
   return ret;
}

void* aligned_alloc(size_t alignment, size_t size)
{
   if (real_aligned_alloc == NULL)
   {
      if (atomic_load(&resolving))
         return bootstrap_alloc(size, alignment);
      resolve();
   }
   void* p = real_aligned_alloc(alignment, size);
   record(KIND_MEMALIGN, size);
   return p;
}

void* memalign(size_t alignment, size_t size)
{
   if (real_memalign == NULL)
   {
      if (atomic_load(&resolving))
         return bootstrap_alloc(size, alignment);
      resolve();
   }
   void* p = real_memalign(alignment, size);
   record(KIND_MEMALIGN, size);
   return p;
}

void* valloc(size_t size)
{
   if (real_valloc == NULL)
   {
      if (atomic_load(&resolving))
         return bootstrap_alloc(size, 4096);
      resolve();
   }
   void* p = real_valloc(size);
   record(KIND_MEMALIGN, size);
   return p;
}

void* pvalloc(size_t size)
{
   if (real_pvalloc == NULL)
   {
      if (atomic_load(&resolving))
         return bootstrap_alloc((size + 4095) & ~(size_t) 4095, 4096);
      resolve();
   }
   void* p = real_pvalloc(size);
   record(KIND_MEMALIGN, size);
   return p;
}

/* ---------------------------------------------------------------------------------------------------------------- */
/* Direct mappings (not glibc malloc's own, which uses internal calls). */

void* mmap(void* address, size_t length, int protection, int flags, int fd, off_t offset)
{
   if (real_mmap == NULL)
   {
      if (atomic_load(&resolving))
         return (void*) syscall(SYS_mmap, address, length, protection, flags, fd, offset);
      resolve();
   }
   void* p = real_mmap(address, length, protection, flags, fd, offset);
   record(KIND_MMAP, length);
   return p;
}

#ifdef __LP64__
/* Same function as mmap on 64-bit glibc, exported under its own name. */
void* mmap64(void* address, size_t length, int protection, int flags, int fd, off_t offset)
{
   return mmap(address, length, protection, flags, fd, offset);
}
#endif

int munmap(void* address, size_t length)
{
   if (real_munmap == NULL)
   {
      if (atomic_load(&resolving))
         return (int) syscall(SYS_munmap, address, length);
      resolve();
   }
   record(KIND_MUNMAP, 0);
   return real_munmap(address, length);
}

void* mremap(void* old_address, size_t old_size, size_t new_size, int flags, ...)
{
   void* new_address = NULL;
   if (flags & MREMAP_FIXED)
   {
      va_list args;
      va_start(args, flags);
      new_address = va_arg(args, void*);
      va_end(args);
   }
   if (real_mremap == NULL)
      resolve();
   void* p = real_mremap(old_address, old_size, new_size, flags, new_address);
   record(KIND_MMAP, new_size);
   return p;
}

/* ---------------------------------------------------------------------------------------------------------------- */
/* C++ operator new / new[]: counted once as "new"; the malloc / posix_memalign they make is not counted again. When the
   C++ runtime is not in the global scope (dlsym finds no next definition), the request is served with malloc /
   aligned_alloc directly, which libstdc++'s operator delete (free) releases correctly. Failure aborts: C cannot throw
   std::bad_alloc, and the nothrow forms return null. */

typedef void* (*new_fn)(size_t);
typedef void* (*new_nothrow_fn)(size_t, const void*);
typedef void* (*new_aligned_fn)(size_t, size_t);
typedef void* (*new_aligned_nothrow_fn)(size_t, size_t, const void*);

static void* fallback_new(size_t size, size_t alignment, int nothrow)
{
   if (size == 0)
      size = 1;
   void* p;
   if (alignment > 16)
   {
      size_t rounded = (size + alignment - 1) & ~(alignment - 1);
      p = aligned_alloc(alignment, rounded);
   }
   else
   {
      p = malloc(size);
   }
   if (p == NULL && !nothrow)
      abort();
   return p;
}

#define DEFINE_NEW(symbol, type, params, real_args, size_expr, alignment_expr, nothrow)                                 \
   void* symbol params                                                                                                \
   {                                                                                                                  \
      static type real;                                                                                               \
      static _Atomic int resolved;                                                                                    \
      if (!atomic_load(&resolved))                                                                                    \
      {                                                                                                               \
         real = (type) dlsym(RTLD_NEXT, #symbol);                                                                     \
         atomic_store(&resolved, 1);                                                                                  \
      }                                                                                                               \
      int outer = t_in_hook == 0;                                                                                     \
      if (outer)                                                                                                      \
      {                                                                                                               \
         record(KIND_NEW, size_expr);                                                                                 \
         t_in_hook = HOOK_NEW;                                                                                        \
      }                                                                                                               \
      void* p = real != NULL ? real real_args : fallback_new(size_expr, alignment_expr, nothrow);                    \
      if (outer)                                                                                                      \
         t_in_hook = 0;                                                                                               \
      return p;                                                                                                       \
   }

/* operator new(size_t), new[](size_t) */
DEFINE_NEW(_Znwm, new_fn, (size_t size), (size), size, 0, 0)
DEFINE_NEW(_Znam, new_fn, (size_t size), (size), size, 0, 0)
/* nothrow */
DEFINE_NEW(_ZnwmRKSt9nothrow_t, new_nothrow_fn, (size_t size, const void* tag), (size, tag), size, 0, 1)
DEFINE_NEW(_ZnamRKSt9nothrow_t, new_nothrow_fn, (size_t size, const void* tag), (size, tag), size, 0, 1)
/* aligned (C++17) */
DEFINE_NEW(_ZnwmSt11align_val_t, new_aligned_fn, (size_t size, size_t alignment), (size, alignment), size, alignment, 0)
DEFINE_NEW(_ZnamSt11align_val_t, new_aligned_fn, (size_t size, size_t alignment), (size, alignment), size, alignment, 0)
DEFINE_NEW(_ZnwmSt11align_val_tRKSt9nothrow_t, new_aligned_nothrow_fn, (size_t size, size_t alignment, const void* tag),
           (size, alignment, tag), size, alignment, 1)
DEFINE_NEW(_ZnamSt11align_val_tRKSt9nothrow_t, new_aligned_nothrow_fn, (size_t size, size_t alignment, const void* tag),
           (size, alignment, tag), size, alignment, 1)
