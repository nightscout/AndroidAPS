package info.nightscout.plugins.general.smsCommunicator.otp

enum class OneTimePasswordValidationResult {
    OK,
    ERROR_WRONG_LENGTH,
    ERROR_WRONG_PIN,
    ERROR_WRONG_OTP
}