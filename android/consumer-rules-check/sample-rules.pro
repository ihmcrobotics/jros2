# The sample app's own rules (what any app has): keep its entry point. Everything jros2 needs must come from
# ../consumer-rules.pro, which validate.sh adds for the positive control and leaves out for the negative control.
-keep class us.ihmc.jros2.r8check.R8Sample {
    public static void main(java.lang.String[]);
}
