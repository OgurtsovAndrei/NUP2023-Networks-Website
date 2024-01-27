import com.example.UserData
import com.example.allUsers
import com.example.defaultTrafficLimit
import com.example.stat
import com.example.windowsTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader

fun getDefaultUserData(): UserData = UserData(windowsTemplate.toMutableMap(), defaultTrafficLimit, true)

class TrafficStat {
    private val process: Process
    val defaultScope = CoroutineScope(Dispatchers.Default)
    val eventChannel: Channel<String> = Channel(capacity = Int.MAX_VALUE)
    val data: MutableMap<String, Long> = mutableMapOf()

    init {
        defaultScope.launch {
            while (true) {
                delay(1000)
                println(stat.data.map { Pair(it.value, it.key) }.sortedBy { it.first }.reversed())
            }
        }
        repeat(1) {
            defaultScope.launch {
                while (true) {
                    val line = eventChannel.receive()
                    val headerData = parseLine(line)
                    if (headerData != null) {
                        val sourceIP = headerData.sourceIp
                        val destIP = headerData.destIp
                        data[sourceIP] = data.getOrPut(sourceIP) { 0 } + headerData.length
                        data[destIP] = data.getOrPut(destIP) { 0 } + headerData.length

                        checkIp(sourceIP)
                        checkIp(destIP)
                    }
                }
            }
        }

        process = ProcessBuilder("bash", "-c", script).start()
        val stdInput = BufferedReader(InputStreamReader(process.inputStream))
        val stdError = BufferedReader(InputStreamReader(process.errorStream))

        defaultScope.launch { stdInput.lines().forEach { eventChannel.trySendBlocking(it) } }
        defaultScope.launch { stdError.lines().forEach { eventChannel.trySendBlocking(it) } }
    }

    fun checkIp(userIP: String) {
        if (!userIP.startsWith("10.202.30") and !userIP.startsWith("10.202.10") and !userIP.startsWith("10.202.20")) return
        val userData = allUsers.getOrPut(userIP) { getDefaultUserData() }
        if (data.getOrPut(userIP) { 0 } > userData.trafficLimit) {
            if (userData.isBockedTraffic.compareAndSet(false, true)) Commands.executeCmd(
                Commands.getStopTrafficCmd(userIP), userIP
            )
        }
    }

    fun parseLine(line: String): ParsedData? {
        val regex =
            """(?<time>$timeReg) .* (?<src>$ipReg)$portReg (>|tell) (?<dst>$ipReg)$portReg(.*Flags (?<flags>.*))?, length (?<len>[0-9]+)""".toRegex()
        val result = regex.find(line.trim())?.groupValues ?: return null
        val res = ParsedData(result[1], result[2], result[6], "", result.last().toLong() + 42L)
        return res
    }

    companion object {
        const val script = "${Commands.sudoHost} tcpdump -nn -i ${Commands.wifiInterface} -l -s 0"
        const val ipReg = "([0-9]{1,3}.){3}[0-9]{1,3}"
        const val portReg = "([:.][0-9]{1,5})?"
        const val timeReg = "[0-9]{2}.[0-9]{2}:[0-9]{2}.[0-9]{6}"
    }
}

data class ParsedData(val time: String, val sourceIp: String, val destIp: String, val flags: String, val length: Long)

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val stat = TrafficStat()
        println("Start")
        runBlocking {

            launch {
                while (true) {
                    delay(100)
                    println(stat.data.map { it.value }.sum())
                }
            }
            launch {
                while (true) {
                    delay(5000)
                    println(stat.data.map { Pair(it.value, it.key) }.sortedBy { it.first }.reversed())
                }
            }
        }
    }
}

object Commands {
    const val IF = "wlp0s20f3" // TODO ??
    const val TC = "/sbin/tc" // TODO ??
    const val passwordSsh: String = "1" // TODO
    const val passwordHost: String = "TODO" // TODO
    const val sudoSsh = "echo $passwordSsh | sudo -S"
    const val sudoHost = "echo $passwordHost | sudo -S"
    const val wifiInterface = "any"
    fun getSshCmd(ip: String): String = "ssh student@${getRouterIP(ip)}"
    fun getRouterIP(ip: String): String = if (ip.startsWith("10.202.30")) "10.202.30.2"
    else if (ip.startsWith("10.202.10")) "10.202.10.1" else throw RuntimeException("")

    fun getBlockingIP(ip: String): String = if (ip.startsWith("10.202.30")) "10.202.20.0/24"
    else if (ip.startsWith("10.202.10")) ip else throw RuntimeException("")

    fun getStopTrafficCmd(ip: String): String =
        "$sudoSsh iptables -A FORWARD -s ${getBlockingIP(ip)} ! -d 10.202.30.100 -j DROP\n"

    fun getStartTrafficCmd(ip: String): String =
        "$sudoSsh iptables -D FORWARD -s ${getBlockingIP(ip)} ! -d 10.202.30.100 -j DROP\n"

    fun getUpgradeSpeedCmd(ip: String): String =
        "$stat ssh -tt student@ sudo -S $TC filter add dev $IF protocol ip parent 1:0 prio 1 u32 match ip dst $ip flowid 1:10"

    fun getDowngradeSpeedCmd(ip: String): String =
        "$sudoSsh $TC filter add dev $IF protocol ip parent 1:0 prio 1 u32 match ip dst $ip flowid 1:30"

    fun executeCmd(cmd: String, ip: String) {

        val process = ProcessBuilder("bash", "-c", getSshCmd(ip) + " '$cmd'").start()
        val stdInput = BufferedReader(InputStreamReader(process.inputStream))
        val stdError = BufferedReader(InputStreamReader(process.errorStream))
        stdInput.lines().forEach { println(it) }
        stdError.lines().forEach { System.err.println(it) }

        println("-".repeat(50) + "\n" + getSshCmd(ip) + " '$cmd'\n" + "-".repeat(50))
    }
}