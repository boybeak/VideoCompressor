# VCompressor
The original repository is [zolad/VideoSlimmer](https://github.com/zolad/VideoSlimmer). The original repository do not provide a reachable dependency now. So I copy the main logic and provide dependency in jitpack.

## Install [![](https://jitpack.io/v/boybeak/VideoCompressor.svg)](https://jitpack.io/#boybeak/VideoCompressor)
```groovy
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```
```groovy
implementation 'com.github.boybeak:VideoCompressor:Tag'
```