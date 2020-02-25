# android-network-monitor

[`Library`](https://github.com/dasBikash84/android-network-monitor/blob/master/android-network-monitor/src/main/java/com/dasbikash/android_network_monitor/NetworkMonitor.kt) for monitoring network status and optionally attach listeners on network state change.

[![](https://jitpack.io/v/dasBikash84/android-network-monitor.svg)](https://jitpack.io/#dasBikash84/android-network-monitor)

## Dependency

Add this in your root `build.gradle` file (**not** your module `build.gradle` file):

```gradle
allprojects {
	repositories {
        maven { url "https://jitpack.io" }
    }
}
```

Then, add the library to your module `build.gradle`
```gradle
dependencies {
    implementation 'com.github.dasBikash84:android-network-monitor:latest.release.here'
}
```

## Features
- Enables network availability/connection type checking.
- Optional listener registration on network connection status change.

## Usage example

##### Initialization (To use this utility initialization is mandatory):
```
    NetworkMonitor.init(activity) // from any class
        //or
    NetworkMonitor.init(fragment) // from any class
        //or
    initNetworkMonitor() // from inside of AppCompatActivity/Fragment class body
        
    // Singleton instance of "NetworkMonitor" will be
    //attached with life-cycle of caller "AppCompatActivity" or "parent of caller Fragment".
    //And it will clear itself on said activity destroy.
    //So, it makes best sense to call "init" from launcher activity to ensure
    //"NetworkMonitor" availability throughout entire application. 
```
##### For checking network connection status:
```
    NetworkMonitor.isConnected() 
        // or
    haveNetworkConnection() 
```
##### For checking if connected to wify network:
```
    NetworkMonitor.isOnWify() 
        // or
    isOnWify()
```

##### For checking if connected to cellular network:

```
    NetworkMonitor.isOnMobileDataNetwork() 
        // or
    isOnMobileDataNetwork() 
```

##### To register Network State Listener:

```
    NetworkMonitor.addNetworkStateListener(networkStateListener)
        // or
    addNetworkStateListener(networkStateListener)
```

##### To remove Network State Listener:

```
    NetworkMonitor.removeNetworkStateListener(networkStateListener)
        // or
    removeNetworkStateListener(networkStateListener) 
```

License
--------

    Copyright 2020 Bikash Das(das.bikash.dev@gmail.com)

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
