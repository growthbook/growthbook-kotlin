package com.sdk.growthbook

class Greeting {
    fun greeting(): String {
        return "Hello, ${Platform().platform}!"
    }
}