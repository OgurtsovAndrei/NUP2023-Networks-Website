import com.example.UserData
import com.example.allUsers
import com.example.windowsTemplate
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import java.io.BufferedReader
import java.io.InputStreamReader

class TrafficStat {
    private val process: Process
    val defaultScope = CoroutineScope(Dispatchers.Default)
    val eventChannel: Channel<String> = Channel(capacity = Int.MAX_VALUE)
    val data: MutableMap<String, Long> = mutableMapOf()

    init {
        repeat(3) {
            defaultScope.launch {
                while (true) {
                    val line = eventChannel.receive()
                    val headerData = parseLine(line)
                    if (headerData != null) {
                        val sourceIP = headerData.sourceIp
                        val destIP = headerData.destIp
                        data[sourceIP] = data.getOrDefault(sourceIP, 0) + headerData.length
                        data[destIP] = data.getOrDefault(destIP, 0) + headerData.length

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
        if (!userIP.startsWith("10.202.20") or !userIP.startsWith("10.202.10")) return
        val userData = allUsers.getOrPut(userIP) { UserData(windowsTemplate.toMutableMap(), 8e9.toLong(), true) }
        if (data.getOrDefault(userIP, 0) > userData.trafficLimit) {
            Commands.executeCmd(Commands.getStopTrafficCmd(userIP))
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
        const val script = "echo ${Commands.password} | sudo -S tcpdump -nn -i ${Commands.wifiInterface} -l -s 0"
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
    const val IF = "enp0s8" // TODO ??
    const val TC = "/sbin/tc" // TODO ??
    const val password: String = "TODO" // TODO
    const val wifiInterface = "any"

    fun getStopTrafficCmd(ip: String): String = "echo $password | sudo -S iptables -A FORWARD -d $ip -j DROP\n"
    fun getStartTrafficCmd(ip: String): String = "echo $password | sudo -S iptables -A FORWARD -d $ip -j DROP\n"
    fun getUpgradeSpeedCmd(ip: String): String =
        "echo $password | sudo -S $TC filter add dev $IF protocol ip parent 1:0 prio 1 u32 match ip dst $ip flowid 1:10"

    fun getDowngradeSpeedCmd(ip: String): String =
        "echo $password | sudo -S $TC filter add dev $IF protocol ip parent 1:0 prio 1 u32 match ip dst $ip flowid 1:30"

    fun executeCmd(cmd: String) {
        val process = ProcessBuilder("bash", "-c", cmd).start()
    }
}