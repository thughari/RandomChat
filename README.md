# 🎥 RandomChat

A lightweight, real-time video chat app that randomly connects two users for peer-to-peer video/audio communication.

Built with:
- ⚙️ Java 21 Virtual Threads
- 🚀 Spring Boot + WebSockets (signaling)
- 📹 WebRTC (video/audio)
- 🌐 Vanilla HTML + JS frontend

---

## 🌍 Live Demo here

👉 [Try it now on Render](https://randomchat-hfta.onrender.com/)[![Live](https://img.shields.io/badge/Live-RandomChat-blue?style=flat&logo=render)](https://randomchat-hfta.onrender.com/)

---

## ⚡ Features

- Anonymous 1-to-1 video chat  
- Peer-to-peer media via WebRTC  
- Fast signaling with WebSockets  
- Scalable backend using Java Virtual Threads  

---

## 🛠️ Run Locally

### Backend
```bash
cd RandomChat
./mvnw spring-boot:run
````

### Frontend

Open `http://localhost:8080` in two browser tabs/devices.

---

## 🔧 How It Works

1. Users connect via WebSocket to the backend
2. Backend matches peers and relays SDP/ICE for negotiation
3. WebRTC creates a direct media connection between users
4. STUN server is used for NAT traversal

---

## 📦 Tech Stack

* **Backend**: Java 21, Spring Boot, WebSockets, Virtual Threads
* **Frontend**: HTML, JavaScript, WebRTC
* **STUN Server**: Google public STUN

---

## 🙋‍♂️ Author

**Hari Thatikonda**\
📫 [haribabutatikonda3@gmail.com](mailto:haribabutatikonda3@gmail.com)\
🔗 [GitHub](https://github.com/thughari) | [LinkedIn](https://linkedin.com/in/hari-thatikonda)

---

## 📄 License

MIT — use it freely, improve it collaboratively.