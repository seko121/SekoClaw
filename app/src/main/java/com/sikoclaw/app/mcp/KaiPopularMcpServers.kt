/* Adapted from Kai 9000 PopularMcpServers.kt, Apache-2.0. */
package com.sikoclaw.app.mcp
data class PopularMcpServer(val name:String,val url:String,val description:String)
val kaiPopularMcpServers=listOf(
 PopularMcpServer("Context7","https://mcp.context7.com/mcp","Up-to-date library and framework docs"),
 PopularMcpServer("CoinGecko","https://mcp.api.coingecko.com/mcp","Real-time crypto prices and market data"),
 PopularMcpServer("Manifold Markets","https://api.manifold.markets/v0/mcp","Prediction market data and odds"),
 PopularMcpServer("Fetch","https://remote.mcpservers.org/fetch/mcp","Fetch web content and convert HTML to markdown"),
 PopularMcpServer("DeepWiki","https://mcp.deepwiki.com/mcp","AI-powered docs for any GitHub repo"),
 PopularMcpServer("Sequential Thinking","https://remote.mcpservers.org/sequentialthinking/mcp","Structured step-by-step problem-solving"),
 PopularMcpServer("Find-A-Domain","https://api.findadomain.dev/mcp","Domain availability across many TLDs"),
 PopularMcpServer("SubwayInfo NYC","https://subwayinfo.nyc/mcp","Real-time NYC transit info"),
 PopularMcpServer("Jina AI","https://mcp.jina.ai/v1","URL conversion, web search and image search"),
 PopularMcpServer("Open-Meteo Weather","https://mcp.open-mcp.org/api/server/open-weather@latest/mcp","Global weather forecasts and air quality")
)
