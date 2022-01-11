package com.comllc.cachinglibrary_kmm.sandbox

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.*
import platform.darwin.NSObject
import platform.darwin.nil

actual typealias SandboxFileManager = NSObject

actual inline fun <reified T> SandboxFileManager.saveSandbox(fileName: String, value: @Serializable T) {
    val fileManager = NSFileManager.defaultManager
    val directoryPath = getDirectoryPath()
    val filePath = directoryPath + "/" + fileName

    val json = Json {  }.encodeToString(value)

    val contents = NSData.create(json)

    // TODO override file contents
    fileManager.createFileAtPath(filePath, contents, null)

}
actual inline fun <reified T> SandboxFileManager.getSandbox(fileName: String) : @Serializable T? {
    // TODO get file contents
    return null
}

actual fun SandboxFileManager.deleteSandbox(fileName: String) {

}

inline fun SandboxFileManager.getDirectoryPath() : String {
    val directoryPath = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as? String
    val targetFolderPath = directoryPath + "SandBox-KMM"

    val fileManager = NSFileManager.defaultManager

    //TODO check and create folder

//    if (!fileManager.fileExistsAtPath(targetFolderPath, true)) {
//        fileManager.createDirectoryAtPath(targetFolderPath, nil)
//    }

    return targetFolderPath
}