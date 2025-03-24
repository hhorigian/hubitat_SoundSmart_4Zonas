/**
 *  Hubitat - SoundSmart 4-Zone Audio Matrix Driver
 *  Version 2.1
 *
 *  Features:
 *  - Power status monitoring (PWON/PWOFF)
 *  - Connection status tracking (online/offline)
 *  - Zone control (volume, mute, input selection)
 *  - Scene management
 *  - Automatic reconnection
 *  - Configurable status checks
 *  - Child device support for each zone
 *
 *
 *  1.0  - 24/03/2025  VH Beta 1.0 
*/

metadata {
    definition (
        name: "SoundSmart 4-Zone Audio Matrix", 
        namespace: "TRATO", 
        author: "VH", 
        singleThreaded: true
    ) {
        capability "Switch"
        capability "Initialize"
        capability "Refresh"
        capability "AudioVolume"
        //capability "MusicPlayer"        
        
        // Zone control commands
        command "zoneOn", [[name:"zone*", type: "NUMBER", description: "Zone number (1-4)"]]
        command "zoneOff", [[name:"zone*", type: "NUMBER", description: "Zone number (1-4)"]]
        command "setZoneVolume", [[name:"zone*", type: "NUMBER", description: "Zone number (1-4)"],
                                [name:"volume*", type: "NUMBER", description: "Volume level (0-100)"]]
        command "zoneVolumeUp", [[name:"zone*", type: "NUMBER", description: "Zone number (1-4)"]]
        command "zoneVolumeDown", [[name:"zone*", type: "NUMBER", description: "Zone number (1-4)"]]
        command "zoneMuteToggle", [[name:"zone*", type: "NUMBER", description: "Zone number (1-4)"]]
        command "setZoneInput", [[name:"zone*", type: "NUMBER", description: "Zone number (1-4)"],
                               [name:"input*", type: "NUMBER", description: "Input number (1-4)"]]
        command "setAllZonesInput", [[name:"input*", type: "NUMBER", description: "Input number (1-4)"]]
        command "saveScene", [[name:"scene*", type: "NUMBER", description: "Scene number (0-9)"]]
        command "recallScene", [[name:"scene*", type: "NUMBER", description: "Scene number (0-9)"]]
        command "checkPowerStatus"
        command "createChildDevices"        
        command "updateChildDevice", [[name:"zone*", type: "NUMBER", description: "Zone number (1-4)"],
                                    [name:"attribute*", type: "STRING", description: "Attribute name"],
                                    [name:"value*", type: "STRING", description: "Attribute value"]]
		command "defaultzones" //puts all inputs in corresponding zones (1-1, 2-2)        
        command "componentOn", [[name:"childDevice", type: "DEVICE"]]
        command "componentOff", [[name:"childDevice", type: "DEVICE"]]
        command "componentSetVolume", [[name:"childDevice", type: "DEVICE"], [name:"volume", type: "NUMBER"]]
        command "componentVolumeUp", [[name:"childDevice", type: "DEVICE"]]
        command "componentVolumeDown", [[name:"childDevice", type: "DEVICE"]]
        command "componentMute", [[name:"childDevice", type: "DEVICE"]]
        command "componentSetInput", [[name:"childDevice", type: "DEVICE"], [name:"input", type: "NUMBER"]]
            
        
        // Attributes
        attribute "powerStatus", "string" // on/off
        attribute "connectionStatus", "string" // online/offline
        attribute "lastStatusCheck", "string" // timestamp
        
        // Zone attributes
        attribute "zone1Status", "string"
        attribute "zone1Volume", "number"
        attribute "zone1Mute", "string"
        attribute "zone1Input", "number"
        attribute "zone2Status", "string"
        attribute "zone2Volume", "number"
        attribute "zone2Mute", "string"
        attribute "zone2Input", "number"
        attribute "zone3Status", "string"
        attribute "zone3Volume", "number"
        attribute "zone3Mute", "string"
        attribute "zone3Input", "number"
        attribute "zone4Status", "string"
        attribute "zone4Volume", "number"
        attribute "zone4Mute", "string"
        attribute "zone4Input", "number"
    }

    preferences {
        input "device_IP_address", "text", title: "SoundSmart IP Address", required: true 
        input "device_port", "number", title: "IP Port", required: true, defaultValue: 2000
        input "checkInterval", "number", title: "Status Check Interval (seconds)", defaultValue: 90, required: true
        input "responseTimeout", "number", title: "Response Timeout (seconds)", defaultValue: 10, required: true
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
        input name: "logInfo", type: "bool", title: "Show Info Logs?", defaultValue: true
        input name: "enableNotifications", type: "bool", title: "Enable Notifications?", defaultValue: false
        input name: "autoCreateChildren", type: "bool", title: "Auto-create child devices?", defaultValue: false
	    input name: "statusUpdateInterval", type: "number", title: "Status Update Interval (seconds)", defaultValue: 30, required: true
        
    }
}

def installed() {
    logInfo("Driver installed")
    initialize()
}

def updated() {
    logInfo("Configuration updated")
    initialize()
    if (autoCreateChildren && !getChildDevices()) {
        createChildDevices()
    }
}

def uninstalled() {
    logInfo("Driver uninstalled")
    unschedule()
    interfaces.rawSocket.close()
    // Remove all child devices
    getChildDevices()?.each { deleteChildDevice(it.deviceNetworkId) }
}

def initialize() {
    logInfo("Initializing driver")
    unschedule()
    interfaces.rawSocket.close()
    
    // Set initial status
    sendEvent(name: "connectionStatus", value: "offline")
    sendEvent(name: "powerStatus", value: "unknown")
    
    if (!device_IP_address || !device_port) {
        logError("IP address or port not configured")
        return
    }

    try {
        logInfo("Connecting to ${device_IP_address}:${device_port}")
        interfaces.rawSocket.connect(device_IP_address, device_port.toInteger())
        state.lastMessageReceivedAt = now()
        markDeviceOnline()
        
        // Schedule regular checks
        runIn(5, "startStatusChecker") // Initial delay before first check
        runIn(checkInterval, "connectionCheck")
        runIn(statusUpdateInterval, "periodicStatusUpdate") // Add status update schedule
        
        
    } catch (Exception e) {
        logError("Connection failed: ${e.message}")
        markDeviceOffline()
        runIn(60, "initialize") // Retry after 60 seconds
    }
}

// Create child devices for each zone
def createChildDevices() {
    logInfo("Creating child devices for zones")
    try {
        for (int i = 1; i <= 4; i++) {
            def childDni = "${device.deviceNetworkId}-zone${i}"
            if (!getChildDevice(childDni)) {
                def childDevice = addChildDevice(
                    "TRATO", // Using the same namespace as parent
                    "SoundSmart Zone Child Device", 
                    childDni,
                    [
                        name: "${device.displayName} Zone ${i}",
                        label: "${device.displayName} Zone ${i}",
                        isComponent: false
                    ]
                )
                childDevice.sendEvent(name: "mute", value: "off")
                childDevice.sendEvent(name: "volume", value: 0)
                childDevice.sendEvent(name: "switch", value: "off")
                childDevice.sendEvent(name: "input", value: 1)
                logInfo("Created child device for Zone ${i}")
            } else {
                logDebug("Child device for Zone ${i} already exists")
            }
        }
    } catch (Exception e) {
        logError("Failed to create child devices: ${e.message}")
    }
}


// Command to DefaultInputs 
def defaultzones() {
    log.debug "Setting All Zones to Default Inputs"
    def command = "All#.\r\n"
    sendCommand(command)
}


// Update child device attributes
def updateChildDevice(zone, attribute, value) {
    def childDevice = getChildDevice("${device.deviceNetworkId}-zone${zone}")
    if (childDevice) {
        childDevice.sendEvent(name: attribute, value: value)
        logDebug("Updated child device Zone ${zone} ${attribute} to ${value}")
    } else {
        logDebug("No child device found for Zone ${zone}")
    }
}

def startStatusChecker() {
    logDebug("Starting status checker")
    unschedule("checkPowerStatus")
    checkPowerStatus()
    runIn(checkInterval, "checkPowerStatus") // Schedule next check
}



def periodicStatusUpdate() {
    logDebug("Running periodic status update")
    try {
        // Request full status update
        queryStatus()
        
        // Reschedule the next update
        runIn(statusUpdateInterval, "periodicStatusUpdate")
    } catch (Exception e) {
        logError("Periodic status update failed: ${e.message}")
        // Attempt to reschedule anyway
        runIn(statusUpdateInterval, "periodicStatusUpdate")
    }
}


def checkPowerStatus() {
    if (state.checkingPower) {
        logDebug("Power check already in progress")
        return
    }
    
    logDebug("Checking power status")
    state.checkingPower = true
    
    try {
        sendCommand("PWGet.\r\n")
        state.powerCheckTimeout = runIn(responseTimeout, "handlePowerCheckTimeout")
    } catch (Exception e) {
        logError("Failed to send power check: ${e.message}")
        markDeviceOffline()
        state.checkingPower = false
    }
}

def handlePowerCheckTimeout() {
    if (state.checkingPower) {
        logWarn("Power status check timed out")
        markDeviceOffline()
        state.checkingPower = false
    }
}

def connectionCheck() {
    def now = now()
    def timeSinceLastMessage = now - (state.lastMessageReceivedAt ?: 0)
    
    if (timeSinceLastMessage > (checkInterval * 1000)) {
        logWarn("No messages received for ${timeSinceLastMessage/1000} seconds")
        markDeviceOffline()
        initialize() // Attempt reconnect
    } else {
        logDebug("Connection verified")
        markDeviceOnline()
    }
    
    runIn(checkInterval, "connectionCheck") // Reschedule
}

def parse(String message) {
    def newmsg = hubitat.helper.HexUtils.hexStringToByteArray(message)
    def newmsg2 = new String(newmsg) 
    
    logDebug("Received: ${newmsg2.trim()}")
    state.lastMessageReceivedAt = now()
    unschedule("handlePowerCheckTimeout")
    markDeviceOnline()
    
    newmsg2.eachLine { line ->
        line = line.trim()
        switch(line) {
            case "PWON":
                handlePowerOn()
                break
            case "PWOFF":
                handlePowerOff()
                break
            case { it.startsWith("A:") }:
                parseZoneLine(line)
                break
        }
    }
    
    if (state.checkingPower) {
        state.checkingPower = false
        sendEvent(name: "lastStatusCheck", value: new Date().format("yyyy-MM-dd HH:mm:ss"))
    }
    
    state.lastmessage = newmsg2
    log.info "Last Msg: " + newmsg2
}

private parseZoneLine(String fullMessage) {
    // Split the full message into individual lines
    def lines = fullMessage.split(/A: /)
    lines.each { line ->
        line = line.trim()
        if (!line) return
        
        // Extract zone number (first 2 digits)
        def zone = line.substring(0, 2).replaceAll(/\D/, '')
        if (!zone.isNumber() || zone.toInteger() < 1 || zone.toInteger() > 4) return
        
        def zoneNum = zone.toInteger()
        def zoneAttr = "zone${zone}"
    
          
        // Check for input assignment (01->001)
        if (line.contains("->")) {
            def inputPart = line.find(/\d+->(\d+)/)
            if (inputPart) {
                def input = inputPart.split(/->/)[1]
                def inputNum = input.toInteger()
                sendEvent(name: "${zoneAttr}Input", value: inputNum)
                updateChildDevice(zoneNum, "input", inputNum)
                logDebug("Zone ${zone} input set to ${inputNum}")
            }
        }
        
      else if (line.contains("Volume")) {
            def volume = line.find(/Volume\s+(\d+)/) { match, vol -> vol }?.toInteger()
            if (volume != null) {
		        def percent = convertDeviceVolumeToPercent(volume)
                sendEvent(name: "${zoneAttr}Volume", value: percent)
                updateChildDevice(zoneNum, "volume", percent)
      			logDebug("Zone ${zone} volume set to ${percent}% (device value: ${volume}), ")
                
            }
    }
          else if (line.contains("Mute")) {
				def muteStatus = line.find(/Mute\s+(\w+)/) { match, status -> status }?.toLowerCase()
                if (muteStatus) {
                sendEvent(name: "${zoneAttr}Mute", value: muteStatus)
                updateChildDevice(zoneNum, "mute", muteStatus)
                logDebug("Zone ${zone} mute ${muteStatus}")
            }
        } 
	}
}//func private    


// Command to POWER ON
def on() {
    logDebug "Setting power to ON"
    def command = "PWON.\r\n"
    sendCommand(command)
    sendEvent(name: "powerStatus", value: "on")
    sendEvent(name: "switch", value: "on")
}

// Command to POWER OFF
def off() {
    logDebug "Setting power to OFF"
    def command = "PWOFF.\r\n"
    sendCommand(command)
    sendEvent(name: "powerStatus", value: "off")
    sendEvent(name: "switch", value: "off")
}

// Zone control commands
def zoneOn(zone) {
    validateZone(zone)
    sendCommand("${zone}@.\r\n")
    logInfo("Zone ${zone} turned on")
    updateChildDevice(zone.toInteger(), "switch", "on")
}

def zoneOff(zone) {
    validateZone(zone)
    sendCommand("${zone}\$.\r\n")
    logInfo("Zone ${zone} turned off")
    updateChildDevice(zone.toInteger(), "switch", "off")
}

def setZoneVolume(zone, percent) {
    validateZone(zone)
    def deviceVolume = convertPercentToDeviceVolume(percent)
    sendCommand("${zone}Vol${deviceVolume}.\r\n")
    logInfo("Zone ${zone} volume set to ${percent}% (device value: ${deviceVolume})")
    updateChildDevice(zone.toInteger(), "volume", percent) // Store percent value in child
}

def zoneVolumeUp(zone) {
    validateZone(zone)
    sendCommand("${zone}VolUp.\r\n")
    logInfo("Zone ${zone} volume increased")
}

def zoneVolumeDown(zone) {
    validateZone(zone)
    sendCommand("${zone}VolDown.\r\n")
    logInfo("Zone ${zone} volume decreased")
}

def zoneMuteToggle(zone) {
    validateZone(zone)
    sendCommand("${zone}MuteTOG.\r\n")
    logInfo("Zone ${zone} mute toggled")
}

def setZoneInput(zone, input) {
    validateZone(zone)
    validateInput(input)
    sendCommand("${input}A${zone}.\r\n")
    logInfo("Zone ${zone} input set to ${input}")
    updateChildDevice(zone.toInteger(), "input", input)
}

def setAllZonesInput(input) {
    validateInput(input)
    sendCommand("${input}All.\r\n")
    logInfo("All zones input set to ${input}")
    (1..4).each { zone ->
        updateChildDevice(zone, "input", input)
    }
}

// Scene management
def saveScene(scene) {
    validateScene(scene)
    sendCommand("Save${scene}.\r\n")
    logInfo("Scene ${scene} saved")
}

def recallScene(scene) {
    validateScene(scene)
    sendCommand("Recall${scene}.\r\n")
    logInfo("Scene ${scene} recalled")
}

def refresh() {
    logInfo("Refreshing status")
    queryStatus()
}

def queryStatus() {
    sendCommand("Status.\r\n")
    logDebug("Requested full status update")
}

// Helper methods
private validateZone(zone) {
    if (zone < 1 || zone > 4) {
        throw new IllegalArgumentException("Zone must be 1-4")
    }
}

private validateInput(input) {
    if (input < 1 || input > 4) {
        throw new IllegalArgumentException("Input must be 1-4")
    }
}

private validateScene(scene) {
    if (scene < 0 || scene > 9) {
        throw new IllegalArgumentException("Scene must be 0-9")
    }
}

private sendCommand(String command) {
    logDebug("Sending: ${command.trim()}")
    interfaces.rawSocket.sendMessage(command)
}

private handlePowerOn() {
    sendEvent(name: "powerStatus", value: "on")
    sendEvent(name: "switch", value: "on")
    logInfo("Device power is ON")
}

private handlePowerOff() {
    sendEvent(name: "powerStatus", value: "off")
    sendEvent(name: "switch", value: "off")
    logInfo("Device power is OFF")
}

private markDeviceOnline() {
    if (device.currentValue("connectionStatus") != "online") {
        sendEvent(name: "connectionStatus", value: "online")
        logInfo("Device is online")
        if (enableNotifications) {
            sendPush("SoundSmart is online")
        }
    }
}

private markDeviceOffline() {
    if (device.currentValue("connectionStatus") != "offline") {
        sendEvent(name: "connectionStatus", value: "offline")
        sendEvent(name: "powerStatus", value: "unknown")
        logWarn("Device is offline")
        if (enableNotifications) {
            sendPush("SoundSmart is offline")
        }
    }
}

// Logging methods
void logDebug(msg) {
    if (logEnable) log.debug "${device.displayName}: ${msg}"
}

void logInfo(msg) {
    if (logInfo != false) log.info "${device.displayName}: ${msg}"
}

void logWarn(msg) {
    log.warn "${device.displayName}: ${msg}"
}

void logError(msg) {
    log.error "${device.displayName}: ${msg}"
}

def logsOff() {
    log.warn "Debug logging disabled"
    device.updateSetting("logEnable", [value:"false", type:"bool"])
}



/**
 *  Child Device Component Commands
 *  These methods handle commands coming from child devices
 */

// Handle volume commands from child device
void componentSetVolume(cd, volume) {
    def zone = getZoneNumberFromDNI(cd.deviceNetworkId)
    if (zone) {
        logDebug("Child device ${cd.deviceNetworkId} for Zone ${zone} requested volume set to ${volume}")
        setZoneVolume(zone, volume)
    } else {
        logError("Could not determine zone from child device ${cd.deviceNetworkId}")
    }
}

// Handle volume up command from child device
void componentVolumeUp(cd) {
    def zone = getZoneNumberFromDNI(cd.deviceNetworkId)    
    if (zone) {
        logDebug("Child device ${cd.deviceNetworkId} for Zone ${zone} requested volume up")
        zoneVolumeUp(zone)
    } else {
        logError("Could not determine zone from child device ${cd.deviceNetworkId}")
    }
}


void componentVolumeDown(cd) {
    def zone = getZoneNumberFromDNI(cd.deviceNetworkId)
    if (zone) {
        logDebug("Child device ${cd.deviceNetworkId} for Zone ${zone} requested volume down")
        zoneVolumeDown(zone)
    } else {
        logError("Could not determine zone from child device ${cd.deviceNetworkId}")
    }
}
    
void componentMute(cd) {
    def zone = getZoneNumberFromDNI(cd.deviceNetworkId)
    if (zone) {
        logDebug("Child device ${cd.deviceNetworkId} for Zone ${zone} requested mute toggle")
        zoneMuteToggle(zone)
    } else {
        logError("Could not determine zone from child device ${cd.deviceNetworkId}")
    }
}


// Handle on command from child device
void componentOn(cd) {
    def zone = getZoneNumberFromDNI(cd.deviceNetworkId)
    if (zone) {
        logDebug("Child device ${cd.deviceNetworkId} for Zone ${zone} requested ON")
        zoneOn(zone)
    } else {
        logError("Could not determine zone from child device ${cd.deviceNetworkId}")
    }
}

// Handle off command from child device
void componentOff(cd) {
    def zone = getZoneNumberFromDNI(cd.deviceNetworkId)
    if (zone) {
        logDebug("Child device ${cd.deviceNetworkId} for Zone ${zone} requested OFF")
        zoneOff(zone)
    } else {
        logError("Could not determine zone from child device ${cd.deviceNetworkId}")
    }
}

void componentSetInput(cd, input) {
    def zone = getZoneNumberFromDNI(cd.deviceNetworkId)
    if (zone) {
        logDebug("Child device ${cd.deviceNetworkId} for Zone ${zone} requested input change to ${input}")
        setZoneInput(zone, input)
    } else {
        logError("Could not determine zone from child device ${cd.deviceNetworkId}")
    }
}

// Helper method to extract zone number from child device DNI
private Integer getZoneNumberFromDNI(dni) {
    try {
        def zone = dni?.toString()?.split("-zone")?.last()
        return zone?.isInteger() ? zone.toInteger() : null
    } catch (Exception e) {
        logError("Error parsing zone from DNI ${dni}: ${e.message}")
        return null
    }
}


// Convert 0-100% to 0-32 device value
private Integer convertPercentToDeviceVolume(percent) {
    percent = Math.max(0, Math.min(100, percent.toInteger()))
    return Math.round(percent * 32 / 100)
}

// Convert 0-32 device value to 0-100%
private Integer convertDeviceVolumeToPercent(deviceVolume) {
    deviceVolume = Math.max(0, Math.min(32, deviceVolume.toInteger()))
    return Math.round(deviceVolume * 100 / 32)
}

