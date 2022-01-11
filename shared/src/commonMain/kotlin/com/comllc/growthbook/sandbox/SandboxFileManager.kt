package com.comllc.cachinglibrary_kmm.sandbox

import kotlinx.serialization.Serializable

expect class SandboxFileManager

expect inline fun <reified T> SandboxFileManager.saveSandbox(fileName: String, value: @Serializable T)

expect inline fun <reified T> SandboxFileManager.getSandbox(fileName: String) : @Serializable T?

expect fun SandboxFileManager.deleteSandbox(fileName: String)

