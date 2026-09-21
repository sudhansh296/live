package com.coderlobby.hivo.auth

/** "+91 98•••••210" style text for the code screen, so the full number is not shown on screen. */
fun maskPhone(dialCode: Int, nationalNumber: String): String {
    val digits = nationalNumber.filter { it.isDigit() }
    if (digits.length <= 5) return "+$dialCode $digits"
    return "+$dialCode ${digits.take(2)}${"•".repeat(digits.length - 5)}${digits.takeLast(3)}"
}
