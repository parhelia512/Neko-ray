package com.neko.v2ray.handler

import android.content.Context
import android.text.TextUtils
import android.util.Log
import com.neko.v2ray.AppConfig
import com.neko.v2ray.AppConfig.DEFAULT_NETWORK
import com.neko.v2ray.AppConfig.DNS_ALIDNS_ADDRESSES
import com.neko.v2ray.AppConfig.DNS_ALIDNS_DOMAIN
import com.neko.v2ray.AppConfig.DNS_CLOUDFLARE_ADDRESSES
import com.neko.v2ray.AppConfig.DNS_CLOUDFLARE_DOMAIN
import com.neko.v2ray.AppConfig.DNS_DNSPOD_ADDRESSES
import com.neko.v2ray.AppConfig.DNS_DNSPOD_DOMAIN
import com.neko.v2ray.AppConfig.DNS_GOOGLE_ADDRESSES
import com.neko.v2ray.AppConfig.DNS_GOOGLE_DOMAIN
import com.neko.v2ray.AppConfig.DNS_QUAD9_ADDRESSES
import com.neko.v2ray.AppConfig.DNS_QUAD9_DOMAIN
import com.neko.v2ray.AppConfig.DNS_YANDEX_ADDRESSES
import com.neko.v2ray.AppConfig.DNS_YANDEX_DOMAIN
import com.neko.v2ray.AppConfig.GEOIP_CN
import com.neko.v2ray.AppConfig.GEOSITE_CN
import com.neko.v2ray.AppConfig.GEOSITE_PRIVATE
import com.neko.v2ray.AppConfig.GOOGLEAPIS_CN_DOMAIN
import com.neko.v2ray.AppConfig.GOOGLEAPIS_COM_DOMAIN
import com.neko.v2ray.AppConfig.HEADER_TYPE_HTTP
import com.neko.v2ray.AppConfig.LOOPBACK
import com.neko.v2ray.AppConfig.PROTOCOL_FREEDOM
import com.neko.v2ray.AppConfig.TAG_BLOCKED
import com.neko.v2ray.AppConfig.TAG_DIRECT
import com.neko.v2ray.AppConfig.TAG_FRAGMENT
import com.neko.v2ray.AppConfig.TAG_PROXY
import com.neko.v2ray.AppConfig.WIREGUARD_LOCAL_ADDRESS_V4
import com.neko.v2ray.dto.ConfigResult
import com.neko.v2ray.dto.EConfigType
import com.neko.v2ray.dto.NetworkType
import com.neko.v2ray.dto.ProfileItem
import com.neko.v2ray.dto.RulesetItem
import com.neko.v2ray.dto.V2rayConfig
import com.neko.v2ray.dto.V2rayConfig.OutboundBean
import com.neko.v2ray.dto.V2rayConfig.OutboundBean.OutSettingsBean
import com.neko.v2ray.dto.V2rayConfig.OutboundBean.StreamSettingsBean
import com.neko.v2ray.dto.V2rayConfig.RoutingBean.RulesBean
import com.neko.v2ray.extension.isNotNullEmpty
import com.neko.v2ray.fmt.HttpFmt
import com.neko.v2ray.fmt.Hysteria2Fmt
import com.neko.v2ray.fmt.ShadowsocksFmt
import com.neko.v2ray.fmt.SocksFmt
import com.neko.v2ray.fmt.TrojanFmt
import com.neko.v2ray.fmt.VlessFmt
import com.neko.v2ray.fmt.VmessFmt
import com.neko.v2ray.fmt.WireguardFmt
import com.neko.v2ray.util.HttpUtil
import com.neko.v2ray.util.JsonUtil
import com.neko.v2ray.util.Utils

object V2rayConfigManager {
    private var initConfigCache: String? = null

    /**
     * Retrieves the V2ray configuration for the given GUID.
     *
     * @param context The context of the caller.
     * @param guid The unique identifier for the V2ray configuration.
     * @return A ConfigResult object containing the configuration details or indicating failure.
     */
    fun getV2rayConfig(context: Context, guid: String): ConfigResult {
        try {
            val config = MmkvManager.decodeServerConfig(guid) ?: return ConfigResult(false)
            return if (config.configType == EConfigType.CUSTOM) {
                getV2rayCustomConfig(guid, config)
            } else {
                getV2rayNormalConfig(context, guid, config)
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to get V2ray config", e)
            return ConfigResult(false)
        }
    }

    /**
     * Retrieves the speedtest V2ray configuration for the given GUID.
     *
     * @param context The context of the caller.
     * @param guid The unique identifier for the V2ray configuration.
     * @return A ConfigResult object containing the configuration details or indicating failure.
     */
    fun getV2rayConfig4Speedtest(context: Context, guid: String): ConfigResult {
        try {
            val config = MmkvManager.decodeServerConfig(guid) ?: return ConfigResult(false)
            return if (config.configType == EConfigType.CUSTOM) {
                getV2rayCustomConfig(guid, config)
            } else {
                getV2rayNormalConfig4Speedtest(context, guid, config)
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to get V2ray config for speedtest", e)
            return ConfigResult(false)
        }
    }

    /**
     * Retrieves the custom V2ray configuration.
     *
     * @param guid The unique identifier for the V2ray configuration.
     * @param config The profile item containing the configuration details.
     * @return A ConfigResult object containing the result of the configuration retrieval.
     */
    private fun getV2rayCustomConfig(guid: String, config: ProfileItem): ConfigResult {
        val raw = MmkvManager.decodeServerRaw(guid) ?: return ConfigResult(false)
        val domainPort = config.getServerAddressAndPort()
        return ConfigResult(true, guid, raw, domainPort)
    }

    /**
     * Retrieves the normal V2ray configuration.
     *
     * @param context The context in which the function is called.
     * @param guid The unique identifier for the V2ray configuration.
     * @param config The profile item containing the configuration details.
     * @return A ConfigResult object containing the result of the configuration retrieval.
     */
    private fun getV2rayNormalConfig(context: Context, guid: String, config: ProfileItem): ConfigResult {
        val result = ConfigResult(false)

        val address = config.server ?: return result
        if (!Utils.isIpAddress(address)) {
            if (!Utils.isValidUrl(address)) {
                Log.w(AppConfig.TAG, "$address is an invalid ip or domain")
                return result
            }
        }

        val v2rayConfig = initV2rayConfig(context) ?: return result
        v2rayConfig.log.loglevel = MmkvManager.decodeSettingsString(AppConfig.PREF_LOGLEVEL) ?: "warning"
        v2rayConfig.remarks = config.remarks

        inbounds(v2rayConfig)

        val isPlugin = config.configType == EConfigType.HYSTERIA2
        val retOut = outbounds(v2rayConfig, config, isPlugin) ?: return result
        val retMore = moreOutbounds(v2rayConfig, config.subscriptionId, isPlugin)

        routing(v2rayConfig)

        fakedns(v2rayConfig)

        dns(v2rayConfig)

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_LOCAL_DNS_ENABLED) == true) {
            customLocalDns(v2rayConfig)
        }
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED) != true) {
            v2rayConfig.stats = null
            v2rayConfig.policy = null
        }

        resolveProxyDomainsToHosts(v2rayConfig)

        result.status = true
        result.content = JsonUtil.toJsonPretty(v2rayConfig) ?: ""
        result.domainPort = if (retMore.first) retMore.second else retOut.second
        result.guid = guid
        return result
    }

    /**
     * Retrieves the normal V2ray configuration for speedtest.
     *
     * @param context The context in which the function is called.
     * @param guid The unique identifier for the V2ray configuration.
     * @param config The profile item containing the configuration details.
     * @return A ConfigResult object containing the result of the configuration retrieval.
     */
    private fun getV2rayNormalConfig4Speedtest(context: Context, guid: String, config: ProfileItem): ConfigResult {
        val result = ConfigResult(false)

        val address = config.server ?: return result
        if (!Utils.isIpAddress(address)) {
            if (!Utils.isValidUrl(address)) {
                Log.w(AppConfig.TAG, "$address is an invalid ip or domain")
                return result
            }
        }

        val v2rayConfig = initV2rayConfig(context) ?: return result

        val isPlugin = config.configType == EConfigType.HYSTERIA2
        val retOut = outbounds(v2rayConfig, config, isPlugin) ?: return result
        val retMore = moreOutbounds(v2rayConfig, config.subscriptionId, isPlugin)

        v2rayConfig.log.loglevel = MmkvManager.decodeSettingsString(AppConfig.PREF_LOGLEVEL) ?: "warning"
        v2rayConfig.inbounds.clear()
        v2rayConfig.routing.rules.clear()
        v2rayConfig.dns = null
        v2rayConfig.fakedns = null
        v2rayConfig.stats = null
        v2rayConfig.policy = null

        v2rayConfig.outbounds.forEach { key ->
            key.mux = null
        }

        result.status = true
        result.content = JsonUtil.toJsonPretty(v2rayConfig) ?: ""
        result.domainPort = if (retMore.first) retMore.second else retOut.second
        result.guid = guid
        return result
    }

    /**
     * Initializes V2ray configuration.
     *
     * This function loads the V2ray configuration from assets or from a cached value.
     * It first attempts to use the cached configuration if available, otherwise reads
     * the configuration from the "v2ray_config.json" asset file.
     *
     * @param context Android context used to access application assets
     * @return V2rayConfig object parsed from the JSON configuration, or null if the configuration is empty
     */

    private fun initV2rayConfig(context: Context): V2rayConfig? {
        val assets = initConfigCache ?: Utils.readTextFromAssets(context, "v2ray_config.json")
        if (TextUtils.isEmpty(assets)) {
            return null
        }
        initConfigCache = assets
        val config = JsonUtil.fromJson(assets, V2rayConfig::class.java)
        return config
    }

    private fun inbounds(v2rayConfig: V2rayConfig): Boolean {
        try {
            val socksPort = SettingsManager.getSocksPort()

            v2rayConfig.inbounds.forEach { curInbound ->
                if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING) != true) {
                    //bind all inbounds to localhost if the user requests
                    curInbound.listen = LOOPBACK
                }
            }
            v2rayConfig.inbounds[0].port = socksPort
            val fakedns = MmkvManager.decodeSettingsBool(AppConfig.PREF_FAKE_DNS_ENABLED) == true
            val sniffAllTlsAndHttp =
                MmkvManager.decodeSettingsBool(AppConfig.PREF_SNIFFING_ENABLED, true) != false
            v2rayConfig.inbounds[0].sniffing?.enabled = fakedns || sniffAllTlsAndHttp
            v2rayConfig.inbounds[0].sniffing?.routeOnly =
                MmkvManager.decodeSettingsBool(AppConfig.PREF_ROUTE_ONLY_ENABLED, false)
            if (!sniffAllTlsAndHttp) {
                v2rayConfig.inbounds[0].sniffing?.destOverride?.clear()
            }
            if (fakedns) {
                v2rayConfig.inbounds[0].sniffing?.destOverride?.add("fakedns")
            }

            if (Utils.isXray()) {
                v2rayConfig.inbounds.removeAt(1)
            } else {
                val httpPort = SettingsManager.getHttpPort()
                v2rayConfig.inbounds[1].port = httpPort
            }

        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to configure inbounds", e)
            return false
        }
        return true
    }

    private fun outbounds(v2rayConfig: V2rayConfig, config: ProfileItem, isPlugin: Boolean): Pair<Boolean, String>? {
        if (isPlugin) {
            val socksPort = Utils.findFreePort(listOf(100 + SettingsManager.getSocksPort(), 0))
            val outboundNew = V2rayConfig.OutboundBean(
                mux = null,
                protocol = EConfigType.SOCKS.name.lowercase(),
                settings = V2rayConfig.OutboundBean.OutSettingsBean(
                    servers = listOf(
                        V2rayConfig.OutboundBean.OutSettingsBean.ServersBean(
                            address = LOOPBACK,
                            port = socksPort
                        )
                    )
                )
            )
            if (v2rayConfig.outbounds.isNotEmpty()) {
                v2rayConfig.outbounds[0] = outboundNew
            } else {
                v2rayConfig.outbounds.add(outboundNew)
            }
            return Pair(true, outboundNew.getServerAddressAndPort())
        }

        val outbound = getProxyOutbound(config) ?: return null
        val ret = updateOutboundWithGlobalSettings(outbound)
        if (!ret) return null

        if (v2rayConfig.outbounds.isNotEmpty()) {
            v2rayConfig.outbounds[0] = outbound
        } else {
            v2rayConfig.outbounds.add(outbound)
        }

        updateOutboundFragment(v2rayConfig)
        return Pair(true, config.getServerAddressAndPort())
    }

    private fun fakedns(v2rayConfig: V2rayConfig) {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_LOCAL_DNS_ENABLED) == true
            && MmkvManager.decodeSettingsBool(AppConfig.PREF_FAKE_DNS_ENABLED) == true
        ) {
            v2rayConfig.fakedns = listOf(V2rayConfig.FakednsBean())
        }
    }

    private fun routing(v2rayConfig: V2rayConfig): Boolean {
        try {

            v2rayConfig.routing.domainStrategy =
                MmkvManager.decodeSettingsString(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY)
                    ?: "IPIfNonMatch"

            val rulesetItems = MmkvManager.decodeRoutingRulesets()
            rulesetItems?.forEach { key ->
                routingUserRule(key, v2rayConfig)
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to configure routing", e)
            return false
        }
        return true
    }

    private fun routingUserRule(item: RulesetItem?, v2rayConfig: V2rayConfig) {
        try {
            if (item == null || !item.enabled) {
                return
            }

            val rule = JsonUtil.fromJson(JsonUtil.toJson(item), RulesBean::class.java) ?: return

            v2rayConfig.routing.rules.add(rule)

        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to apply routing user rule", e)
        }
    }

    private fun userRule2Domain(tag: String): ArrayList<String> {
        val domain = ArrayList<String>()

        val rulesetItems = MmkvManager.decodeRoutingRulesets()
        rulesetItems?.forEach { key ->
            if (key.enabled && key.outboundTag == tag && !key.domain.isNullOrEmpty()) {
                key.domain?.forEach {
                    if (it != GEOSITE_PRIVATE
                        && (it.startsWith("geosite:") || it.startsWith("domain:"))
                    ) {
                        domain.add(it)
                    }
                }
            }
        }

        return domain
    }

    private fun customLocalDns(v2rayConfig: V2rayConfig): Boolean {
        try {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_FAKE_DNS_ENABLED) == true) {
                val geositeCn = arrayListOf(GEOSITE_CN)
                val proxyDomain = userRule2Domain(TAG_PROXY)
                val directDomain = userRule2Domain(TAG_DIRECT)
                // fakedns with all domains to make it always top priority
                v2rayConfig.dns?.servers?.add(
                    0,
                    V2rayConfig.DnsBean.ServersBean(
                        address = "fakedns",
                        domains = geositeCn.plus(proxyDomain).plus(directDomain)
                    )
                )
            }

            // DNS inbound
            val remoteDns = SettingsManager.getRemoteDnsServers()
            if (v2rayConfig.inbounds.none { e -> e.protocol == "dokodemo-door" && e.tag == "dns-in" }) {
                val dnsInboundSettings = V2rayConfig.InboundBean.InSettingsBean(
                    address = if (Utils.isPureIpAddress(remoteDns.first())) remoteDns.first() else AppConfig.DNS_PROXY,
                    port = 53,
                    network = "tcp,udp"
                )

                val localDnsPort = Utils.parseInt(
                    MmkvManager.decodeSettingsString(AppConfig.PREF_LOCAL_DNS_PORT),
                    AppConfig.PORT_LOCAL_DNS.toInt()
                )
                v2rayConfig.inbounds.add(
                    V2rayConfig.InboundBean(
                        tag = "dns-in",
                        port = localDnsPort,
                        listen = LOOPBACK,
                        protocol = "dokodemo-door",
                        settings = dnsInboundSettings,
                        sniffing = null
                    )
                )
            }

            // DNS outbound
            if (v2rayConfig.outbounds.none { e -> e.protocol == "dns" && e.tag == "dns-out" }) {
                v2rayConfig.outbounds.add(
                    V2rayConfig.OutboundBean(
                        protocol = "dns",
                        tag = "dns-out",
                        settings = null,
                        streamSettings = null,
                        mux = null
                    )
                )
            }

            // DNS routing tag
            v2rayConfig.routing.rules.add(
                0, RulesBean(
                    inboundTag = arrayListOf("dns-in"),
                    outboundTag = "dns-out",
                    domain = null
                )
            )
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to configure custom local DNS", e)
            return false
        }
        return true
    }

    private fun dns(v2rayConfig: V2rayConfig): Boolean {
        try {
            val hosts = mutableMapOf<String, Any>()
            val servers = ArrayList<Any>()

            //remote Dns
            val remoteDns = SettingsManager.getRemoteDnsServers()
            val proxyDomain = userRule2Domain(TAG_PROXY)
            remoteDns.forEach {
                servers.add(it)
            }
            if (proxyDomain.isNotEmpty()) {
                servers.add(
                    V2rayConfig.DnsBean.ServersBean(
                        address = remoteDns.first(),
                        domains = proxyDomain,
                    )
                )
            }

            // domestic DNS
            val domesticDns = SettingsManager.getDomesticDnsServers()
            val directDomain = userRule2Domain(TAG_DIRECT)
            val isCnRoutingMode = directDomain.contains(GEOSITE_CN)
            val geoipCn = arrayListOf(GEOIP_CN)
            if (directDomain.isNotEmpty()) {
                servers.add(
                    V2rayConfig.DnsBean.ServersBean(
                        address = domesticDns.first(),
                        domains = directDomain,
                        expectIPs = if (isCnRoutingMode) geoipCn else null,
                        skipFallback = true
                    )
                )
            }

            if (Utils.isPureIpAddress(domesticDns.first())) {
                v2rayConfig.routing.rules.add(
                    0, RulesBean(
                        outboundTag = TAG_DIRECT,
                        port = "53",
                        ip = arrayListOf(domesticDns.first()),
                        domain = null
                    )
                )
            }

            //User DNS hosts
            try {
                val userHosts = MmkvManager.decodeSettingsString(AppConfig.PREF_DNS_HOSTS)
                if (userHosts.isNotNullEmpty()) {
                    var userHostsMap = userHosts?.split(",")
                        ?.filter { it.isNotEmpty() }
                        ?.filter { it.contains(":") }
                        ?.associate { it.split(":").let { (k, v) -> k to v } }
                    if (userHostsMap != null) hosts.putAll(userHostsMap)
                }
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to configure user DNS hosts", e)
            }

            //block dns
            val blkDomain = userRule2Domain(TAG_BLOCKED)
            if (blkDomain.isNotEmpty()) {
                hosts.putAll(blkDomain.map { it to LOOPBACK })
            }

            // hardcode googleapi rule to fix play store problems
            hosts[GOOGLEAPIS_CN_DOMAIN] = GOOGLEAPIS_COM_DOMAIN

            // hardcode popular Android Private DNS rule to fix localhost DNS problem
            hosts[DNS_ALIDNS_DOMAIN] = DNS_ALIDNS_ADDRESSES
            hosts[DNS_CLOUDFLARE_DOMAIN] = DNS_CLOUDFLARE_ADDRESSES
            hosts[DNS_DNSPOD_DOMAIN] = DNS_DNSPOD_ADDRESSES
            hosts[DNS_GOOGLE_DOMAIN] = DNS_GOOGLE_ADDRESSES
            hosts[DNS_QUAD9_DOMAIN] = DNS_QUAD9_ADDRESSES
            hosts[DNS_YANDEX_DOMAIN] = DNS_YANDEX_ADDRESSES


            // DNS dns
            v2rayConfig.dns = V2rayConfig.DnsBean(
                servers = servers,
                hosts = hosts
            )

            // DNS routing
            if (Utils.isPureIpAddress(remoteDns.first())) {
                v2rayConfig.routing.rules.add(
                    0, RulesBean(
                        outboundTag = TAG_PROXY,
                        port = "53",
                        ip = arrayListOf(remoteDns.first()),
                        domain = null
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to configure DNS", e)
            return false
        }
        return true
    }

    private fun updateOutboundWithGlobalSettings(outbound: V2rayConfig.OutboundBean): Boolean {
        try {
            var muxEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_MUX_ENABLED, false)
            val protocol = outbound.protocol
            if (protocol.equals(EConfigType.SHADOWSOCKS.name, true)
                || protocol.equals(EConfigType.SOCKS.name, true)
                || protocol.equals(EConfigType.HTTP.name, true)
                || protocol.equals(EConfigType.TROJAN.name, true)
                || protocol.equals(EConfigType.WIREGUARD.name, true)
                || protocol.equals(EConfigType.HYSTERIA2.name, true)
            ) {
                muxEnabled = false
            } else if (outbound.streamSettings?.network == NetworkType.XHTTP.type) {
                muxEnabled = false
            }

            if (muxEnabled == true) {
                outbound.mux?.enabled = true
                outbound.mux?.concurrency = MmkvManager.decodeSettingsString(AppConfig.PREF_MUX_CONCURRENCY, "8").orEmpty().toInt()
                outbound.mux?.xudpConcurrency = MmkvManager.decodeSettingsString(AppConfig.PREF_MUX_XUDP_CONCURRENCY, "16").orEmpty().toInt()
                outbound.mux?.xudpProxyUDP443 = MmkvManager.decodeSettingsString(AppConfig.PREF_MUX_XUDP_QUIC, "reject")
                if (protocol.equals(EConfigType.VLESS.name, true) && outbound.settings?.vnext?.first()?.users?.first()?.flow?.isNotEmpty() == true) {
                    outbound.mux?.concurrency = -1
                }
            } else {
                outbound.mux?.enabled = false
                outbound.mux?.concurrency = -1
            }

            if (protocol.equals(EConfigType.WIREGUARD.name, true)) {
                var localTunAddr = if (outbound.settings?.address == null) {
                    listOf(WIREGUARD_LOCAL_ADDRESS_V4)
                } else {
                    outbound.settings?.address as List<*>
                }
                if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PREFER_IPV6) != true) {
                    localTunAddr = listOf(localTunAddr.first())
                }
                outbound.settings?.address = localTunAddr
            }

            if (outbound.streamSettings?.network == DEFAULT_NETWORK
                && outbound.streamSettings?.tcpSettings?.header?.type == HEADER_TYPE_HTTP
            ) {
                val path = outbound.streamSettings?.tcpSettings?.header?.request?.path
                val host = outbound.streamSettings?.tcpSettings?.header?.request?.headers?.Host

                val requestString: String by lazy {
                    """{"version":"1.1","method":"GET","headers":{"User-Agent":["Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36"],"Accept-Encoding":["gzip, deflate"],"Connection":["keep-alive"],"Pragma":"no-cache"}}"""
                }
                outbound.streamSettings?.tcpSettings?.header?.request = JsonUtil.fromJson(
                    requestString,
                    V2rayConfig.OutboundBean.StreamSettingsBean.TcpSettingsBean.HeaderBean.RequestBean::class.java
                )
                outbound.streamSettings?.tcpSettings?.header?.request?.path =
                    if (path.isNullOrEmpty()) {
                        listOf("/")
                    } else {
                        path
                    }
                outbound.streamSettings?.tcpSettings?.header?.request?.headers?.Host = host
            }


        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to update outbound with global settings", e)
            return false
        }
        return true
    }

    private fun updateOutboundFragment(v2rayConfig: V2rayConfig): Boolean {
        try {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_FRAGMENT_ENABLED, false) == false) {
                return true
            }
            if (v2rayConfig.outbounds[0].streamSettings?.security != AppConfig.TLS
                && v2rayConfig.outbounds[0].streamSettings?.security != AppConfig.REALITY
            ) {
                return true
            }

            val fragmentOutbound =
                V2rayConfig.OutboundBean(
                    protocol = PROTOCOL_FREEDOM,
                    tag = TAG_FRAGMENT,
                    mux = null
                )

            var packets =
                MmkvManager.decodeSettingsString(AppConfig.PREF_FRAGMENT_PACKETS) ?: "tlshello"
            if (v2rayConfig.outbounds[0].streamSettings?.security == AppConfig.REALITY
                && packets == "tlshello"
            ) {
                packets = "1-3"
            } else if (v2rayConfig.outbounds[0].streamSettings?.security == AppConfig.TLS
                && packets != "tlshello"
            ) {
                packets = "tlshello"
            }

            fragmentOutbound.settings = V2rayConfig.OutboundBean.OutSettingsBean(
                fragment = V2rayConfig.OutboundBean.OutSettingsBean.FragmentBean(
                    packets = packets,
                    length = MmkvManager.decodeSettingsString(AppConfig.PREF_FRAGMENT_LENGTH)
                        ?: "50-100",
                    interval = MmkvManager.decodeSettingsString(AppConfig.PREF_FRAGMENT_INTERVAL)
                        ?: "10-20"
                ),
                noises = listOf(
                    V2rayConfig.OutboundBean.OutSettingsBean.NoiseBean(
                        type = "rand",
                        packet = "10-20",
                        delay = "10-16",
                    )
                ),
            )
            fragmentOutbound.streamSettings = V2rayConfig.OutboundBean.StreamSettingsBean(
                sockopt = V2rayConfig.OutboundBean.StreamSettingsBean.SockoptBean(
                    TcpNoDelay = true,
                    mark = 255
                )
            )
            v2rayConfig.outbounds.add(fragmentOutbound)

            //proxy chain
            v2rayConfig.outbounds[0].streamSettings?.sockopt =
                V2rayConfig.OutboundBean.StreamSettingsBean.SockoptBean(
                    dialerProxy = TAG_FRAGMENT
                )
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to update outbound fragment", e)
            return false
        }
        return true
    }

    private fun moreOutbounds(
        v2rayConfig: V2rayConfig,
        subscriptionId: String,
        isPlugin: Boolean
    ): Pair<Boolean, String> {
        val returnPair = Pair(false, "")
        var domainPort: String = ""

        if (isPlugin) {
            return returnPair
        }
        //fragment proxy
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_FRAGMENT_ENABLED, false) == true) {
            return returnPair
        }

        if (subscriptionId.isEmpty()) {
            return returnPair
        }
        try {
            val subItem = MmkvManager.decodeSubscription(subscriptionId) ?: return returnPair

            //current proxy
            val outbound = v2rayConfig.outbounds[0]

            //Previous proxy
            val prevNode = SettingsManager.getServerViaRemarks(subItem.prevProfile)
            if (prevNode != null) {
                val prevOutbound = getProxyOutbound(prevNode)
                if (prevOutbound != null) {
                    updateOutboundWithGlobalSettings(prevOutbound)
                    prevOutbound.tag = TAG_PROXY + "2"
                    v2rayConfig.outbounds.add(prevOutbound)
                    outbound.ensureSockopt().dialerProxy = prevOutbound.tag
                    domainPort = prevNode.getServerAddressAndPort()
                }
            }

            //Next proxy
            val nextNode = SettingsManager.getServerViaRemarks(subItem.nextProfile)
            if (nextNode != null) {
                val nextOutbound = getProxyOutbound(nextNode)
                if (nextOutbound != null) {
                    updateOutboundWithGlobalSettings(nextOutbound)
                    nextOutbound.tag = TAG_PROXY
                    v2rayConfig.outbounds.add(0, nextOutbound)
                    outbound.tag = TAG_PROXY + "1"
                    nextOutbound.ensureSockopt().dialerProxy = outbound.tag
                    if (nextNode.configType == EConfigType.WIREGUARD) {
                        domainPort = nextNode.getServerAddressAndPort()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to configure more outbounds", e)
            return returnPair
        }

        if (domainPort.isNotEmpty()) {
            return Pair(true, domainPort)
        }
        return returnPair
    }

    private fun resolveProxyDomainsToHosts(v2rayConfig: V2rayConfig) {
        val proxyOutboundList = v2rayConfig.getAllProxyOutbound()
        val dns = v2rayConfig.dns ?: return

        val newHosts = dns.hosts?.toMutableMap() ?: mutableMapOf()

        val preferIpv6 = MmkvManager.decodeSettingsBool(AppConfig.PREF_PREFER_IPV6) == true

        for (item in proxyOutboundList) {
            val domain = item.getServerAddress()
            if (domain.isNullOrEmpty()) continue

            if (newHosts.containsKey(domain)) continue

            val resolvedIps = HttpUtil.resolveHostToIP(
                domain,
                preferIpv6
            )

            if (resolvedIps.isEmpty()) continue

            item.ensureSockopt().domainStrategy =
                if (preferIpv6) "UseIPv6v4" else "UseIPv4v6"

            newHosts[domain] = if (resolvedIps.size == 1) {
                resolvedIps[0]
            } else {
                resolvedIps
            }
        }

        dns.hosts = newHosts
    }

    /**
     * Retrieves the proxy outbound configuration for the given profile item.
     *
     * @param profileItem The profile item for which to get the proxy outbound configuration.
     * @return The proxy outbound configuration as a V2rayConfig.OutboundBean, or null if not found.
     */
    private fun getProxyOutbound(profileItem: ProfileItem): V2rayConfig.OutboundBean? {
        return when (profileItem.configType) {
            EConfigType.VMESS -> VmessFmt.toOutbound(profileItem)
            EConfigType.CUSTOM -> null
            EConfigType.SHADOWSOCKS -> ShadowsocksFmt.toOutbound(profileItem)
            EConfigType.SOCKS -> SocksFmt.toOutbound(profileItem)
            EConfigType.VLESS -> VlessFmt.toOutbound(profileItem)
            EConfigType.TROJAN -> TrojanFmt.toOutbound(profileItem)
            EConfigType.WIREGUARD -> WireguardFmt.toOutbound(profileItem)
            EConfigType.HYSTERIA2 -> Hysteria2Fmt.toOutbound(profileItem)
            EConfigType.HTTP -> HttpFmt.toOutbound(profileItem)
        }
    }

    fun createOutbound(configType: EConfigType): OutboundBean? {
        return when (configType) {
            EConfigType.VMESS,
            EConfigType.VLESS ->
                return OutboundBean(
                    protocol = configType.name.lowercase(),
                    settings = OutSettingsBean(
                        vnext = listOf(
                            OutSettingsBean.VnextBean(
                                users = listOf(OutSettingsBean.VnextBean.UsersBean())
                            )
                        )
                    ),
                    streamSettings = StreamSettingsBean()
                )

            EConfigType.SHADOWSOCKS,
            EConfigType.SOCKS,
            EConfigType.HTTP,
            EConfigType.TROJAN,
            EConfigType.HYSTERIA2 ->
                return OutboundBean(
                    protocol = configType.name.lowercase(),
                    settings = OutSettingsBean(
                        servers = listOf(OutSettingsBean.ServersBean())
                    ),
                    streamSettings = StreamSettingsBean()
                )

            EConfigType.WIREGUARD ->
                return OutboundBean(
                    protocol = configType.name.lowercase(),
                    settings = OutSettingsBean(
                        secretKey = "",
                        peers = listOf(OutSettingsBean.WireGuardBean())
                    )
                )

            EConfigType.CUSTOM -> null
        }
    }

    fun populateTransportSettings(streamSettings: StreamSettingsBean, profileItem: ProfileItem): String? {
        val transport = profileItem.network.orEmpty()
        val headerType = profileItem.headerType
        val host = profileItem.host
        val path = profileItem.path
        val seed = profileItem.seed
//        val quicSecurity = profileItem.quicSecurity
//        val key = profileItem.quicKey
        val mode = profileItem.mode
        val serviceName = profileItem.serviceName
        val authority = profileItem.authority
        val xhttpMode = profileItem.xhttpMode
        val xhttpExtra = profileItem.xhttpExtra

        var sni: String? = null
        streamSettings.network = if (transport.isEmpty()) NetworkType.TCP.type else transport
        when (streamSettings.network) {
            NetworkType.TCP.type -> {
                val tcpSetting = StreamSettingsBean.TcpSettingsBean()
                if (headerType == AppConfig.HEADER_TYPE_HTTP) {
                    tcpSetting.header.type = AppConfig.HEADER_TYPE_HTTP
                    if (!TextUtils.isEmpty(host) || !TextUtils.isEmpty(path)) {
                        val requestObj = StreamSettingsBean.TcpSettingsBean.HeaderBean.RequestBean()
                        requestObj.headers.Host = host.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        requestObj.path = path.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        tcpSetting.header.request = requestObj
                        sni = requestObj.headers.Host?.getOrNull(0)
                    }
                } else {
                    tcpSetting.header.type = "none"
                    sni = host
                }
                streamSettings.tcpSettings = tcpSetting
            }

            NetworkType.KCP.type -> {
                val kcpsetting = StreamSettingsBean.KcpSettingsBean()
                kcpsetting.header.type = headerType ?: "none"
                if (seed.isNullOrEmpty()) {
                    kcpsetting.seed = null
                } else {
                    kcpsetting.seed = seed
                }
                if (host.isNullOrEmpty()) {
                    kcpsetting.header.domain = null
                } else {
                    kcpsetting.header.domain = host
                }
                streamSettings.kcpSettings = kcpsetting
            }

            NetworkType.WS.type -> {
                val wssetting = StreamSettingsBean.WsSettingsBean()
                wssetting.headers.Host = host.orEmpty()
                sni = host
                wssetting.path = path ?: "/"
                streamSettings.wsSettings = wssetting
            }

            NetworkType.HTTP_UPGRADE.type -> {
                val httpupgradeSetting = StreamSettingsBean.HttpupgradeSettingsBean()
                httpupgradeSetting.host = host.orEmpty()
                sni = host
                httpupgradeSetting.path = path ?: "/"
                streamSettings.httpupgradeSettings = httpupgradeSetting
            }

            NetworkType.XHTTP.type -> {
                val xhttpSetting = StreamSettingsBean.XhttpSettingsBean()
                xhttpSetting.host = host.orEmpty()
                sni = host
                xhttpSetting.path = path ?: "/"
                xhttpSetting.mode = xhttpMode
                xhttpSetting.extra = JsonUtil.parseString(xhttpExtra)
                streamSettings.xhttpSettings = xhttpSetting
            }

            NetworkType.H2.type, NetworkType.HTTP.type -> {
                streamSettings.network = NetworkType.H2.type
                val h2Setting = StreamSettingsBean.HttpSettingsBean()
                h2Setting.host = host.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
                sni = h2Setting.host.getOrNull(0)
                h2Setting.path = path ?: "/"
                streamSettings.httpSettings = h2Setting
            }

//                    "quic" -> {
//                        val quicsetting = QuicSettingBean()
//                        quicsetting.security = quicSecurity ?: "none"
//                        quicsetting.key = key.orEmpty()
//                        quicsetting.header.type = headerType ?: "none"
//                        quicSettings = quicsetting
//                    }

            NetworkType.GRPC.type -> {
                val grpcSetting = StreamSettingsBean.GrpcSettingsBean()
                grpcSetting.multiMode = mode == "multi"
                grpcSetting.serviceName = serviceName.orEmpty()
                grpcSetting.authority = authority.orEmpty()
                grpcSetting.idle_timeout = 60
                grpcSetting.health_check_timeout = 20
                sni = authority
                streamSettings.grpcSettings = grpcSetting
            }
        }
        return sni
    }

    fun populateTlsSettings(streamSettings: StreamSettingsBean, profileItem: ProfileItem, sniExt: String?) {
        val streamSecurity = profileItem.security.orEmpty()
        val allowInsecure = profileItem.insecure == true
        val sni = if (profileItem.sni.isNullOrEmpty()) sniExt else profileItem.sni
        val fingerprint = profileItem.fingerPrint
        val alpns = profileItem.alpn
        val publicKey = profileItem.publicKey
        val shortId = profileItem.shortId
        val spiderX = profileItem.spiderX

        streamSettings.security = if (streamSecurity.isEmpty()) null else streamSecurity
        if (streamSettings.security == null) return
        val tlsSetting = StreamSettingsBean.TlsSettingsBean(
            allowInsecure = allowInsecure,
            serverName = if (sni.isNullOrEmpty()) null else sni,
            fingerprint = if (fingerprint.isNullOrEmpty()) null else fingerprint,
            alpn = if (alpns.isNullOrEmpty()) null else alpns.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            publicKey = if (publicKey.isNullOrEmpty()) null else publicKey,
            shortId = if (shortId.isNullOrEmpty()) null else shortId,
            spiderX = if (spiderX.isNullOrEmpty()) null else spiderX,
        )
        if (streamSettings.security == AppConfig.TLS) {
            streamSettings.tlsSettings = tlsSetting
            streamSettings.realitySettings = null
        } else if (streamSettings.security == AppConfig.REALITY) {
            streamSettings.tlsSettings = null
            streamSettings.realitySettings = tlsSetting
        }
    }
}
