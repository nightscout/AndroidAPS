package app.aaps.core.keys.interfaces

/**
 * Validator for string preference values.
 * Used to validate input before accepting it.
 */
fun interface StringValidator {

    /**
     * Validates the input string.
     * @param value The string to validate
     * @return ValidationResult indicating if valid and error message if not
     */
    fun validate(value: String): ValidationResult

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    ) {

        companion object {

            val VALID = ValidationResult(true)
            fun invalid(message: String) = ValidationResult(false, message)
        }
    }

    companion object {

        /** No validation - always valid */
        val NONE = StringValidator { ValidationResult.VALID }

        /** Validates that string is not empty */
        fun notEmpty(errorMessage: String = "Cannot be empty") = StringValidator { value ->
            if (value.isNotEmpty()) ValidationResult.VALID
            else ValidationResult.invalid(errorMessage)
        }

        /** Validates against a regex pattern */
        fun regex(pattern: String, errorMessage: String = "Invalid format") = StringValidator { value ->
            if (value.isEmpty() || Regex(pattern).matches(value)) ValidationResult.VALID
            else ValidationResult.invalid(errorMessage)
        }

        /** Validates against a regex pattern, requiring non-empty */
        fun regexRequired(pattern: String, errorMessage: String = "Invalid format") = StringValidator { value ->
            if (value.isEmpty()) ValidationResult.invalid("Cannot be empty")
            else if (Regex(pattern).matches(value)) ValidationResult.VALID
            else ValidationResult.invalid(errorMessage)
        }

        /** Validates minimum length */
        fun minLength(min: Int, errorMessage: String? = null) = StringValidator { value ->
            if (value.length >= min) ValidationResult.VALID
            else ValidationResult.invalid(errorMessage ?: "Minimum $min characters required")
        }

        /** Validates email format */
        fun email(errorMessage: String = "Invalid email address") = regex(
            pattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
            errorMessage = errorMessage
        )

        /** Validates URL format (https only) */
        fun httpsUrl(errorMessage: String = "Invalid HTTPS URL") = regex(
            pattern = "^https://.*",
            errorMessage = errorMessage
        )

        /** Validates phone number (allows digits, spaces, dashes, plus) */
        fun phone(errorMessage: String = "Invalid phone number") = regex(
            pattern = "^[+]?[0-9\\s\\-()]+$",
            errorMessage = errorMessage
        )

        /** Validates multiple phone numbers separated by semicolon */
        fun multiPhone(errorMessage: String = "Invalid phone number format") = StringValidator { value ->
            if (value.isEmpty()) return@StringValidator ValidationResult.VALID
            val phones = value.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            val phoneRegex = Regex("^[+]?[0-9\\s\\-()]+$")
            if (phones.all { phoneRegex.matches(it) }) ValidationResult.VALID
            else ValidationResult.invalid(errorMessage)
        }

        /** Validates hexadecimal string */
        fun hexadecimal(errorMessage: String = "Invalid hexadecimal value") = regex(
            pattern = "^[0-9A-Fa-f]+$",
            errorMessage = errorMessage
        )

        /** Validates person name (letters, spaces, hyphens, apostrophes) */
        fun personName(errorMessage: String = "Invalid name format") = regex(
            pattern = "^[a-zA-Z\\s'-]+$",
            errorMessage = errorMessage
        )

        /** Validates PIN strength (minimum 6 digits) */
        fun pinStrength(errorMessage: String = "PIN must be at least 6 digits") = StringValidator { value ->
            if (value.isEmpty()) return@StringValidator ValidationResult.VALID
            if (value.length < 6) return@StringValidator ValidationResult.invalid("PIN must be at least 6 digits")
            if (!Regex("^[0-9]+$").matches(value)) return@StringValidator ValidationResult.invalid("PIN must contain only digits")
            ValidationResult.VALID
        }

        /** Combines multiple validators - all must pass */
        fun all(vararg validators: StringValidator) = StringValidator { value ->
            for (validator in validators) {
                val result = validator.validate(value)
                if (!result.isValid) return@StringValidator result
            }
            ValidationResult.VALID
        }
    }
}
