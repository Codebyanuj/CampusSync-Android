# Add project specific ProGuard rules here.
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.firebase.* <methods>;
}
