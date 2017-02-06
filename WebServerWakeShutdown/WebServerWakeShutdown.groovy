/**
 *  Webserver WOL Switch
 *
 *  Copyright 2017 Gerrod Bland
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
def version() {
	return "0.1 (20170205)\n© 2017 Gerrod Bland"
}

preferences {
	input("confMACAddr", "string", title:"WOL MAC Address",
		required: true, displayDuringSetup: true)
	input("confIpAddr", "string", title:"Device Local IP Address",
		required: true, displayDuringSetup: true)
	input("confTcpPort", "number", title:"TCP Port",
		defaultValue:"80", required: true, displayDuringSetup: true)
	input("confURI", "string", title:"Shutdown URI Path",
		required: true, displayDuringSetup: true)
	input("confPingTimeout", "number", title:"Ping Timeout (Seconds)",
		defaultValue:"2", required: true, displayDuringSetup: true)
	input(title:"", description: "Version: ${version()}", type: "paragraph", element: "paragraph")
}

metadata {
	definition (name: "Webserver WOL Switch", namespace: "geggog", author: "Gerrod Bland") {
		capability "Switch"
        capability "Refresh"

		attribute "lastRun", "number"
        // attribute "lastStatus", "string"
	}

	simulator {
		// TODO: define status and reply messages here
	}

    tiles(scale: 2) {
        multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#79b821", nextState:"turningOff"
                attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
                attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#79b821", nextState:"turningOff"
                attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
            }
        }
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        main (["switch"])
        details(["switch", "refresh"])
    }
    
}

// refresh state of the NAS, best configured to be called via Pollster
def refresh() {

	log.debug "Executing 'refresh'"

	// "ping" network device
	localHttpGet("/")

	// test whether we get a response
	runIn(settings.confPingTimeout, lastRun)

	log.debug "Executing 'refresh' completed."
    
}

// record lastRun
def lastRun() {

	// determine if lastRun was withing the last two seconds
	def currentTime = new GregorianCalendar().time.time
	def lastRun = device.latestValue("lastRun")
    
    // log.debug "Current time: "
    // log.debug currentTime
    // log.debug "Last run time: "
    // log.debug lastRun
    
    if ((currentTime - lastRun) > (settings.confPingTimeout * 1000)) {
    	log.debug "Network device hasn't responded within ${settings.confPingTimeout} seconds, marking off(line)."
		sendEvent(name: "switch", value: "off")
        
    }
    
    // record last run time
    sendEvent(name: 'lastRun', value: currentTime)

}

// parse events into attributes
def parse(String description) {

	log.debug "Executing 'parse'."
    // log.debug "Parsing '${description}'"
	
    // given we've received a response, set device to up if it isn't already
    if (device.latestValue("switch") != "on") {

        // parse is only ever called when a response is received, mark device as on
        sendEvent(name: "switch", value: "on")
        
	} else {
    
		log.debug "Switch already on, no need to re-mark."

    }

    // set last run time
    def currentTime = new GregorianCalendar().time.time
    sendEvent(name: 'lastRun', value: currentTime)

	log.debug "Executing 'parse' completed."
    
}

// handle commands
def on() {

	def wakeString = "wake on lan " + settings.confMACAddr
	log.debug "Executing 'on'; attempting: " + wakeString

    def result = new physicalgraph.device.HubAction (
        //"wake on lan 4c60de24e854",
        wakeString,
        physicalgraph.device.Protocol.LAN,
        null,
        [secureCode: "111122223333"]
    )
    
	sendEvent(name: "switch", value: "on")

	log.debug "Executing 'on' completed."
    
	return result

}

def off() {

	log.debug "Executing 'off'"

	// call shutdown
	localHttpGet("/shutdown")

	sendEvent(name: "switch", value: "off")

	log.debug "Executing 'off' completed."
    
    // return result
    
}

def localHttpGet(path) {

    def hosthex = convertIPtoHex(settings.confIpAddr)
    def porthex = convertPortToHex(settings.confTcpPort)
    device.deviceNetworkId = "$hosthex:$porthex"     
    // log.debug "The device id configured is: $device.deviceNetworkId"

    // def path = "/shutdown"
    // log.debug "path is: $path"
    
    def headers = [:] 
    headers.put("HOST", "$settings.confIpAddr:$settings.confTcpPort")    
    // log.debug "The Header is $headers"
   
  	try {
    
    	def hubAction = new physicalgraph.device.HubAction(
            method: "GET",
            path: path,
            headers: headers
        )
        
        sendHubCommand(hubAction)
        
    } catch (Exception e) {
    	log.debug "Hit Exception $e"
    }
    
}

private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    // log.debug "IP address entered is $ipAddress and the converted hex code is $hex"
    return hex

}

private String convertPortToHex(port) {
	String hexport = port.toString().format( '%04x', port.toInteger() )
    // log.debug "IP address entered is $port and the converted hex code is $hexport"
    return hexport
}