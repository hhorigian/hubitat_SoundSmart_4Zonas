/**
 *  SoundSmart Zone Child Device Driver
 *  Version 1.0
 *
 *  Works with TRATO SoundSmart 4-Zone Audio Matrix driver
 *  Provides individual zone control through parent device
 *  1.0  - 24/03/2025  VH Beta 1.0 
 */

metadata {
    definition (
        name: "SoundSmart Zone Child Device",
        namespace: "TRATO",
        author: "VH",
        singleThreaded: true
    ) {
        capability "Switch"
        capability "AudioVolume"
        capability "Refresh"
        //capability "MusicPlayer"        
        
        attribute "input", "number"
        
        command "setInput", [[name:"input", type: "NUMBER", description: "Input number (1-4)"]]
        command "volumeUp"
        command "volumeDown"
        command "mute"
        command "unmute"
    }

    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
    }
}

def installed() {
    log.info "SoundSmart Zone Child Device installed"
    initialize()
}

def updated() {
    log.info "SoundSmart Zone Child Device configuration updated"
    initialize()
}

def initialize() {
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "volume", value: 0)
    sendEvent(name: "mute", value: "unmuted")
    sendEvent(name: "input", value: 1)
}

def on() {
    if (logEnable) log.debug "Turning zone on"
    try {
        parent.componentOn(device)
    } catch (Exception e) {
        log.error "Error in on(): ${e.message}"
    }
}

def off() {
    if (logEnable) log.debug "Turning zone off"
    try {
        parent.componentOff(device)
    } catch (Exception e) {
        log.error "Error in off(): ${e.message}"
    }
}

def setVolume(percent) {
    if (logEnable) log.debug "Setting volume to ${percent}%"
    percent = Math.max(0, Math.min(100, percent.toInteger()))
    try {
        parent?.componentSetVolume(device, percent)
    } catch (Exception e) {
        log.error "Error in setVolume(): ${e.message}"
    }
}

def volumeUp() {
    if (logEnable) log.debug "Increasing volume"
    try {
        parent.componentVolumeUp(device)
    } catch (Exception e) {
        log.error "Error in volumeUp(): ${e.message}"
    }
}

def volumeDown() {
    if (logEnable) log.debug "Decreasing volume"
    try {
        parent.componentVolumeDown(device)
    } catch (Exception e) {
        log.error "Error in volumeDown(): ${e.message}"
    }
}

def mute() {
    if (logEnable) log.debug "Muting zone"
    try {
        parent.componentMute(device)
    } catch (Exception e) {
        log.error "Error in mute(): ${e.message}"
    }
}

def unmute() {
    if (logEnable) log.debug "Unmuting zone"
    try {
        parent.componentMute(device) // Toggle mute since device doesn't have separate unmute command
    } catch (Exception e) {
        log.error "Error in unmute(): ${e.message}"
    }
}

def setInput(input) {
    if (logEnable) log.debug "Setting input to ${input}"
    try {
        parent.componentSetInput(device, input.toInteger())
    } catch (Exception e) {
        log.error "Error in setInput(): ${e.message}"
    }
}


def refresh() {
    if (logEnable) log.debug "Refreshing zone status"
    try {
        parent?.componentRefresh(device)
    } catch (Exception e) {
        log.error "Error in refresh(): ${e.message}"
    }
}

def ping() {
    refresh()
}

// Handle updates from parent device
def updateStatus(status) {
    if (logEnable) log.debug "Received status update: ${status}"
    
    status.each { key, value ->
        switch(key) {
            case "switch":
                sendEvent(name: "switch", value: value)
                break
            case "volume":
                // Convert device volume (0-32) to percent (0-100) if needed
                def percent = (value <= 32) ? parent?.convertDeviceVolumeToPercent(value) : value
                sendEvent(name: "volume", value: percent)
                break
            case "mute":
                sendEvent(name: "mute", value: value ? "muted" : "unmuted")
                break
            case "input":
                sendEvent(name: "input", value: value.toInteger())
                break
        }
    }
}
