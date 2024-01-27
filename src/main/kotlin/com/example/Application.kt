package com.example

import Commands
import TrafficStat
import getDefaultUserData
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.concurrent.atomic.AtomicBoolean

const val defaultTrafficLimit = 8e9.toLong()


data class Window(
    var id: Int,
    var selected: Boolean,
    val title: String,
    val text: String,
    val buttonId: String,
    val trafficLimit: Long,
    val speedLimit: Boolean
) {
    fun onTap(userIp: String, windows: MutableMap<String, Window>) {
        println("---> $buttonId button clicked by user with IP: $userIp <---")
        windows.values.forEach { it.selected = it.buttonId == this.buttonId }
        val userData = allUsers.get(userIp) ?: return
        userData.trafficLimit = this.trafficLimit
        userData.speedLimit = this.speedLimit
        reloadIpStatus(userIp, userData)
    }
}

fun reloadIpStatus(ip: String, userData: UserData) {
    if (!ip.startsWith("10.202.30") and !ip.startsWith("10.202.10") and !ip.startsWith("10.202.20")) return
    if (stat.data.getOrPut(ip) { 0 } < userData.trafficLimit) {
        if (userData.isBockedTraffic.compareAndSet(true, false)) Commands.executeCmd(Commands.getStartTrafficCmd(ip), ip) }
    else {if (userData.isBockedTraffic.compareAndSet(false, true)) Commands.executeCmd(Commands.getStopTrafficCmd(ip), ip) }
    if (userData.speedLimit) { Commands.executeCmd(Commands.getDowngradeSpeedCmd(ip), ip) }
    else { Commands.executeCmd(Commands.getUpgradeSpeedCmd(ip), ip) }
}

typealias Windows = MutableMap<String, Window>

data class UserData(val userWindows: Windows, var trafficLimit: Long, var speedLimit: Boolean, val isBockedTraffic: AtomicBoolean = AtomicBoolean(false))

val windowsTemplate = mutableMapOf<String, Window>(
    "A" to Window(1, false, "Title A", "Text description of window A", "A", trafficLimit = defaultTrafficLimit, speedLimit = false),
    "B" to Window(2, false, "Title B", "Text description of window B", "B", trafficLimit = Long.MAX_VALUE, speedLimit = true),
    "C" to Window(3, false, "Title C", "Text description of window C", "C", trafficLimit = Long.MAX_VALUE, speedLimit = false),
)
var allUsers: MutableMap<String, UserData> = mutableMapOf("localhost" to UserData(windowsTemplate.toMutableMap(), Long.MAX_VALUE, false))


fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

val pageTitle = "My Page"
val pageDescription = "This is a sample web page with interactive buttons."
val stat = TrafficStat()

fun Application.module() {
    install(Routing) {
        static("/static") {
            resources("files")
        }

        get("/") {
            call.respondText(
                buildString {
                    append(
                        """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>$pageTitle</title>
                    <style>
                    /* Changes made to the styles omitted for brevity */
                    .center-text {
                        text-align: center;
                    }
                    /* Remaining styles... */
                    </style>
                    <script>
                    
                        // add fetch request change the selected value
                        function buttonTap(buttonType) {
                            fetch("/"+buttonType).then(() => {
                                window.location.reload();
                                console.log(buttonType + " button clicked.");
                            });
                        }
                        
                        function refreshTraffic() {
                            fetch("/getTraffic")   // getting traffic details from server
                            .then(response => response.text())
                            .then(data => {
                                document.getElementById('traffic-details').innerText = data;   // updating innerText content
                            });
                        }
                        
                        function refreshPage() {
                            location.reload();
                        }
                        
                        window.onload = function() {
                            setInterval(refreshTraffic, 100);
                        }
                        
                    </script>
                    </head>
                    <body>
                </head>
                <body>
                    <h1 class="center-text">$pageTitle</h1>
                    <p class="center-text">$pageDescription</p>
                    <div style="display: flex; justify-content: space-around;">
                """.trimIndent()
                    )
                    var userIp = call.request.origin.remoteHost
                    val userData = allUsers.getOrPut(userIp) { getDefaultUserData() }
                    val windows = userData.userWindows

                    for (window in windows.values) {
                        val color = if (window.selected) "green" else "red"
                        append(
                            """
                            <div class="window">
                                <div style="height: 25px; width: 25px; border-radius: 50%; background-color: $color;"></div>
                                <h2>${window.title}</h2>
                                <p>${window.text}</p>
                                <button onclick="buttonTap('${window.buttonId}')">Button ${window.buttonId}</button>
                            </div>
                            """.trimIndent()
                        )
                    }


                    append(
                        """
                        <div class="window">
                            <h2>Traffic Details</h2>
                            <p id="traffic-details">Loading traffic statistic...</p> 
                        </div>
                        """.trimIndent()
                    )

                    append("</div></body></html>")

                },
                ContentType.Text.Html
            )
        }

        get("/getTraffic") {
            val userIp = call.request.origin.remoteHost
            val amount = getTraffic(userIp)
            call.respondText(
                buildString {
                    append("\nTraffic used by $userIp is: $amount")
                },
                contentType = ContentType.Text.Plain
            )
        }

        for (windowTemp in windowsTemplate.values) {
            get("/${windowTemp.buttonId}") {
                val userIp = call.request.origin.remoteHost
                val userData = allUsers.getOrPut(userIp) { getDefaultUserData() }
                val windows = userData.userWindows
                val window = windows[windowTemp.buttonId]
                window!!.onTap(userIp, windows)
                call.respondText(window.buttonId + " button clicked")
            }
        }
    }
}

fun getTraffic(userIp: String): String {
    var userIp = userIp
    if (userIp == "localhost") { userIp = "192.168.10.8" }

    val traffic = stat.data[userIp] ?: 0L
    val amount: String = when {
        traffic <= 1_000 -> { "$traffic Bytes" }
        traffic <= 1_000_000 -> { "${traffic / 1024} KBytes" }
        traffic <= 1_000_000_000 -> {"${traffic / 1024 / 1024} MBytes"}
        else -> {"${traffic / 1024 / 1024 / 1024} GBytes"}
    }
    return amount
}