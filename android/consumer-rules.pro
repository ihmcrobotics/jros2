# R8 / ProGuard rules shipped inside the jros2-android AAR and applied automatically to every app that depends on it
# when the app enables minification (isMinifyEnabled = true). They keep only what jros2 looks up by name at runtime.
# Validated with R8 itself (positive and negative control): android/consumer-rules-check/validate.sh

# ---------------------------------------------------------------------------------------------------------------------
# JNI
# libjnifastddsjava.so exports Java_us_ihmc_fastddsjava_natives_fastddsjava_<method> symbols, so the class name and every
# native method name must survive. The listeners also call FindClass("us/ihmc/fastddsjava/natives/fastddsjavaCallback")
# and GetMethodID(..., "call", "()V") and invoke call() on jros2's callback objects (lambdas). Keeping the interface method
# also keeps call() in every implementation, including desugared lambdas (verified by validate.sh), so no extra
# "implements fastddsjavaCallback" rule is needed. The whole (small) natives package is kept so that later JNI additions
# there are covered as well.
-keep class us.ihmc.fastddsjava.natives.** { *; }

# ---------------------------------------------------------------------------------------------------------------------
# Reflection on generated message classes (us.ihmc.jros2.ROS2Message)
#  - ROS2Message.getNameFromMessageClass:     Class.getField("name")      (DDS type name, e.g. "std_msgs::msg::dds_::String_")
#  - ROS2Message.getTypeHashFromMessageClass: Class.getField("typeHash")  (RIHS01 type hash advertised in USER_DATA)
#  - ROS2Message.createInstance:              Class.getDeclaredConstructor().newInstance()
#  - ROS2Message.getHeaderMethod:             Class.getDeclaredMethods(), first method returning std_msgs.Header
-keepclassmembers class * implements us.ihmc.jros2.ROS2Message {
    public static final java.lang.String name;
    public static final java.lang.String typeHash;
    public <init>();
    public std_msgs.Header *();
}

# IDLObjectSequence creates nested elements of any CDRSerializable type with Class.getDeclaredConstructor().newInstance().
-keepclassmembers class * implements us.ihmc.fastddsjava.cdr.CDRSerializable {
    public <init>();
}
