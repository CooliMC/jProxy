# jProxy - A simple java-based Proxy-Server

### Features

#### Http(s) Proxy:
- Simple block-source-list for public block lists like Pi-hole
- Minimum RAM usage (1,000,000 domains ≈ 100 MB)
- Simple Http-Proxy implementation in Java
- Fast ***isBlocked()*** check with HashSets
- Console-based and lightweight
- Simple block-list for own blocks
- HTTPS support for secure connections

#### Socks4(a) Proxy:
- Support of Socks4(a) protocol
- CONNECT and BIND command working
- Configuration of command availability

#### Socks5 Proxy:
- Support of Socks5 authentication
- Account management with username and password
- Configuration of command availability

### Roadmap

- Adding Sock5-Implementation
- Add generic "regex-based" domain check
