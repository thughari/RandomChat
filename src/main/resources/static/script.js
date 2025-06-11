// --- UI Elements ---
    const appContainer = document.getElementById('app-container');
    const videoContainer = document.getElementById('video-container');
    const localVideo = document.getElementById("localVideo");
    const remoteVideo = document.getElementById("remoteVideo");
    const statusOverlay = document.getElementById("status-overlay");
    const statusText = document.getElementById("status-text");
    const remoteStatusContainer = document.getElementById('remote-status-container');
    const controls = document.getElementById('controls');
    
    const micBtn = document.getElementById("mic-btn");
    const videoBtn = document.getElementById("video-btn");
    const selfViewBtn = document.getElementById("self-view-btn");
    const fullscreenBtn = document.getElementById("fullscreen-btn");
    const disconnectBtn = document.getElementById("disconnect-btn");

    // --- State & Config ---
    let peerConnection;
    let localStream;
    let remoteIceCandidatesQueue = [];
    let controlsTimeout;
    const instanceId = Math.random().toString(36).substring(7);
    console.log(`[${instanceId}] Initializing Client`);

    // --- UI/Controls Logic ---
    function updateStatus(message, show = true) {
      statusText.textContent = message;
      show ? statusOverlay.classList.remove("hidden") : statusOverlay.classList.add("hidden");
    }
    
    function setControlsEnabled(enabled) {
        micBtn.disabled = !enabled;
        videoBtn.disabled = !enabled;
        selfViewBtn.disabled = !enabled;
        disconnectBtn.disabled = !enabled;
    }

    function hideControls() {
        controls.classList.add('hidden');
    }

    function showControls() {
        controls.classList.remove('hidden');
        clearTimeout(controlsTimeout);
        controlsTimeout = setTimeout(hideControls, 3000); // Hide after 3 seconds
    }

    videoContainer.addEventListener('mousemove', showControls);
    videoContainer.addEventListener('mouseleave', hideControls);
    videoContainer.addEventListener('touchstart', showControls, { passive: true });
    
    function updateRemoteMediaStatus(kind, enabled) {
        let iconId = `remote-${kind}-icon`;
        let existingIcon = document.getElementById(iconId);
        if (enabled && existingIcon) {
            existingIcon.remove();
        } else if (!enabled && !existingIcon) {
            const icon = document.createElement('i');
            icon.id = iconId;
            icon.className = `fa-solid fa-${kind === 'audio' ? 'microphone' : 'video'}-slash`;
            remoteStatusContainer.appendChild(icon);
        }
    }

    // --- WebSocket Setup ---
    const wsProtocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const wsHost = (window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1")
                   ? "localhost:8080" : window.location.host;
    const ws = new WebSocket(`${wsProtocol}//${wsHost}/ws`);
    ws.onopen = () => updateStatus("Connected. Waiting for a peer...");
    ws.onclose = () => { updateStatus("Disconnected. Please refresh."); resetConnection(); };
    ws.onerror = () => updateStatus("Connection error. Check console.");

    ws.onmessage = async (message) => {
      console.log(`[${instanceId}] WS Message:`, message.data);
      const data = JSON.parse(message.data);

      switch (data.type) {
        case 'initiateOffer':
          updateStatus("Peer found! Calling...");
          setControlsEnabled(true);
          await ensurePeerConnection();
          await ensureLocalMediaAndTracks();
          const offer = await peerConnection.createOffer();
          await peerConnection.setLocalDescription(offer);
          ws.send(JSON.stringify({ type: 'offer', offer: offer }));
          break;
        case 'waitForOffer':
          updateStatus("Peer found! Waiting for their call...");
          setControlsEnabled(true);
          await ensurePeerConnection();
          await ensureLocalMediaAndTracks();
          break;
        case 'offer':
          setControlsEnabled(true);
          await ensurePeerConnection();
          await peerConnection.setRemoteDescription(new RTCSessionDescription(data.offer));
          await processQueuedIceCandidates();
          await ensureLocalMediaAndTracks();
          const answer = await peerConnection.createAnswer();
          await peerConnection.setLocalDescription(answer);
          ws.send(JSON.stringify({ type: 'answer', answer: answer }));
          break;
        case 'answer':
          await peerConnection.setRemoteDescription(new RTCSessionDescription(data.answer));
          await processQueuedIceCandidates();
          break;
        case 'ice':
          if (data.candidate) {
            try {
              if (peerConnection && peerConnection.remoteDescription) {
                await peerConnection.addIceCandidate(new RTCIceCandidate(data.candidate));
              } else { remoteIceCandidatesQueue.push(data.candidate); }
            } catch (e) { console.error(`[${instanceId}] Error adding ICE candidate:`, e); }
          }
          break;
        case 'media_status':
          updateRemoteMediaStatus(data.kind, data.enabled);
          break;
        case 'leave':
          alert("Peer has disconnected.");
          resetConnection();
          updateStatus("Peer disconnected. Waiting for new peer...");
          break;
      }
    };
    
    // --- WebRTC Core Functions ---
    async function ensurePeerConnection() {
      if (peerConnection && peerConnection.signalingState !== "closed") return;
      console.log(`[${instanceId}] Creating new RTCPeerConnection.`);
      remoteIceCandidatesQueue = [];
      const turnConfig = await fetch('/api/turn-config')
        .then(res => res.json())
        .catch(e => {
            console.warn("Could not fetch TURN config:", e);
            return {};
        });
      
      peerConnection = new RTCPeerConnection({
        iceServers: [
          { urls: 'stun:stun.l.google.com:19302' },
          ...(turnConfig.iceServers || [])
        ]
      });
      configurePeerConnectionEventListeners();
    }
    
    function configurePeerConnectionEventListeners() {
      peerConnection.onicecandidate = e => e.candidate && ws.send(JSON.stringify({ type: 'ice', candidate: e.candidate }));
      peerConnection.oniceconnectionstatechange = () => console.log(`[${instanceId}] ICE State: ${peerConnection.iceConnectionState}`);
      peerConnection.ontrack = (event) => {
        console.log(`[${instanceId}] Track received from peer.`);
        if (remoteVideo.srcObject !== event.streams[0]) {
          remoteVideo.srcObject = event.streams[0];
          updateStatus("Call connected!", false);
          if (localStream) {
            sendMediaStatus('audio', localStream.getAudioTracks()[0]?.enabled);
            sendMediaStatus('video', localStream.getVideoTracks()[0]?.enabled);
          }
        }
      };
    }

    async function ensureLocalMediaAndTracks() {
      if (!localStream) await startMedia();
      if (localStream && peerConnection) {
        for (const track of localStream.getTracks()) {
          if (!peerConnection.getSenders().find(s => s.track === track)) {
            peerConnection.addTrack(track, localStream);
          }
        }
      }
    }

    async function processQueuedIceCandidates() {
      while (remoteIceCandidatesQueue.length > 0) {
        const candidate = remoteIceCandidatesQueue.shift();
        if (peerConnection.remoteDescription) {
          await peerConnection.addIceCandidate(candidate);
        } else {
          remoteIceCandidatesQueue.unshift(candidate); break;
        }
      }
    }

    function resetConnection() {
      console.log(`[${instanceId}] Resetting connection.`);
      if (peerConnection) {
        peerConnection.close();
        peerConnection = null;
      }
      remoteVideo.srcObject = null;
      remoteStatusContainer.innerHTML = '';
      setControlsEnabled(false);
      updateStatus("Waiting for a new peer...");
      showControls(); // Show controls for the next session
    }
    
    // --- Media & Control Functions ---
    async function startMedia() {
      try {
        localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
        localVideo.srcObject = localStream;
      } catch (err) {
        updateStatus("Error: " + err.message);
        alert("Could not access camera/microphone: " + err.message);
        throw err;
      }
    }
    
    function sendMediaStatus(kind, enabled) {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'media_status', kind: kind, enabled: enabled }));
      }
    }

    // --- Button Event Listeners ---
    micBtn.addEventListener('click', () => {
      const audioTrack = localStream.getAudioTracks()[0];
      if (!audioTrack) return;
      audioTrack.enabled = !audioTrack.enabled;
      micBtn.classList.toggle('active', audioTrack.enabled);
      micBtn.innerHTML = audioTrack.enabled ? '<i class="fa-solid fa-microphone"></i>' : '<i class="fa-solid fa-microphone-slash"></i>';
      sendMediaStatus('audio', audioTrack.enabled);
    });

    videoBtn.addEventListener('click', () => {
      const videoTrack = localStream.getVideoTracks()[0];
      if (!videoTrack) return;
      videoTrack.enabled = !videoTrack.enabled;
      videoBtn.classList.toggle('active', videoTrack.enabled);
      videoBtn.innerHTML = videoTrack.enabled ? '<i class="fa-solid fa-video"></i>' : '<i class="fa-solid fa-video-slash"></i>';
      sendMediaStatus('video', videoTrack.enabled);
    });

    selfViewBtn.addEventListener('click', () => {
      localVideo.classList.toggle('hidden');
      selfViewBtn.classList.toggle('active', !localVideo.classList.contains('hidden'));
      selfViewBtn.innerHTML = localVideo.classList.contains('hidden') ? '<i class="fa-solid fa-eye-slash"></i>' : '<i class="fa-solid fa-eye"></i>';
    });

    fullscreenBtn.addEventListener('click', () => {
      if (!document.fullscreenElement) {
        document.documentElement.requestFullscreen().catch(err => alert(`Error: ${err.message}`));
      } else {
        document.exitFullscreen();
      }
    });
    
    disconnectBtn.addEventListener('click', () => {
      if (confirm("Are you sure you want to disconnect and find a new peer?")) {
        if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify({ type: 'leave' }));
        resetConnection();
      }
    });

    // --- Initial Page Load ---
    async function initialize() {
      try {
        await startMedia();
        showControls(); // Start the timer on load
      } catch (e) {
        console.error(`[${instanceId}] Initialization failed:`, e);
      }
    }

    initialize();