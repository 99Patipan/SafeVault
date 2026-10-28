package app.safevault.crypto

open class VaultException(message: String) : Exception(message)

/** Malformed, unsupported, or policy-violating vault/header data. */
class VaultFormatException(message: String) : VaultException(message)

/** Wrong password OR modified header/wrapped key (GCM cannot distinguish; intentionally one error). */
class UnlockFailedException : VaultException("Wrong password or vault data was modified")

/** AES-GCM authentication tag mismatch. */
class AuthenticationFailedException : VaultException("Data failed authentication")

/** The device-bound key could not decrypt stored vault data (key lost/invalidated, or stored data modified). */
class DeviceKeyUnavailableException : VaultException("Device key unavailable or stored data modified")
