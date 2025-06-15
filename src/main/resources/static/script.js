const localVideo = document.getElementById("localVideo"),
  remoteVideo = document.getElementById("remoteVideo");
const statusOverlay = document.getElementById("status-overlay"),
  statusText = document.getElementById("status-text");
const videoContainer = document.getElementById("video-container"),
  controls = document.getElementById("controls");
const remoteStatusContainer = document.getElementById(
  "remote-status-container"
);
const notificationDiv = document.getElementById("notification");
const micBtn = document.getElementById("mic-btn"),
  videoBtn = document.getElementById("video-btn");
const selfViewBtn = document.getElementById("self-view-btn"),
  fullscreenBtn = document.getElementById("fullscreen-btn");
const disconnectBtn = document.getElementById("disconnect-btn");

const confirmDisconnectModal = document.getElementById(
  "confirm-disconnect-modal"
);
const confirmDisconnectOkBtn = document.getElementById(
  "confirm-disconnect-ok-btn"
);
const confirmDisconnectCancelBtn = document.getElementById(
  "confirm-disconnect-cancel-btn"
);

// --- State & Config ---
let peerConnection, localStream, controlsTimeout;
let remoteIceCandidatesQueue = [];
const instanceId = Math.random().toString(36).substring(7);
let cachedTurnConfig = null;

// --- Notification Logic ---
function showNotification(message) {
  notificationDiv.textContent = message;
  notificationDiv.classList.add("show");
  setTimeout(() => {
    notificationDiv.classList.remove("show");
  }, 3000);
}

// --- UI Logic ---
function updateStatus(message, show = true) {
  statusText.textContent = message;
  show
    ? statusOverlay.classList.remove("hidden")
    : statusOverlay.classList.add("hidden");
}
function setControlsEnabled(enabled) {
  [micBtn, videoBtn, selfViewBtn, disconnectBtn].forEach(
    (btn) => (btn.disabled = !enabled)
  );
}
function hideControls() {
  controls.classList.add("hidden");
}
function showControls() {
  controls.classList.remove("hidden");
  clearTimeout(controlsTimeout);
  controlsTimeout = setTimeout(hideControls, 3000);
}
videoContainer.addEventListener("mousemove", showControls);
videoContainer.addEventListener("mouseleave", hideControls);
videoContainer.addEventListener("touchstart", showControls, { passive: true });

function updateRemoteMediaStatus(kind, enabled) {
  const iconId = `remote-${kind}-icon`;
  const existingIcon = document.getElementById(iconId);
  if (enabled && existingIcon) existingIcon.remove();
  else if (!enabled && !existingIcon) {
    const icon = document.createElement("i");
    icon.id = iconId;
    icon.className = `fa-solid fa-${
      kind === "audio" ? "microphone" : "video"
    }-slash`;
    remoteStatusContainer.appendChild(icon);
  }
}

// --- WebSocket Setup ---
const wsProtocol = window.location.protocol === "https:" ? "wss:" : "ws:";
const wsHost =
  window.location.hostname === "localhost" ||
  window.location.hostname === "127.0.0.1"
    ? "localhost:8080"
    : window.location.host;
const ws = new WebSocket(`${wsProtocol}//${wsHost}/ws`);
ws.onopen = () => {
  console.log(`[${instanceId}] WebSocket connected.`);
  // --- AUTO-CONNECT ---
  ws.send(JSON.stringify({ type: "ready_for_peer" }));
};
ws.onclose = () => {
  updateStatus("Disconnected. Please refresh.");
  resetConnection();
};
ws.onerror = () => updateStatus("Connection error. Check console.");

ws.onmessage = async (message) => {
  const data = JSON.parse(message.data);
  switch (data.type) {
    case "initiateOffer":
      updateStatus("Partner found! Calling...", false);
      setControlsEnabled(true);
      await ensurePeerConnection();
      await ensureLocalMediaAndTracks();
      const offer = await createOfferWithIceRestart();
      await peerConnection.setLocalDescription(offer);
      ws.send(JSON.stringify({ type: "offer", offer: offer }));
      break;
    case "waitForOffer":
      updateStatus("Partner found! Waiting for their call...", false);
      setControlsEnabled(true);
      await ensurePeerConnection();
      await ensureLocalMediaAndTracks();
      break;
    case "offer":
      setControlsEnabled(true);
      await ensurePeerConnection();
      await peerConnection.setRemoteDescription(
        new RTCSessionDescription(data.offer)
      );
      await processQueuedIceCandidates();
      await ensureLocalMediaAndTracks();
      const answer = await peerConnection.createAnswer();
      await peerConnection.setLocalDescription(answer);
      ws.send(JSON.stringify({ type: "answer", answer: answer }));
      break;
    case "answer":
      await peerConnection.setRemoteDescription(
        new RTCSessionDescription(data.answer)
      );
      await processQueuedIceCandidates();
      break;
    case "ice":
      if (data.candidate) {
        try {
          if (peerConnection?.remoteDescription) {
            await peerConnection.addIceCandidate(
              new RTCIceCandidate(data.candidate)
            );
          } else {
            remoteIceCandidatesQueue.push(data.candidate);
          }
        } catch (e) {
          console.error(`Error adding ICE candidate:`, e);
        }
      }
      break;
    case "media_status":
      updateRemoteMediaStatus(data.kind, data.enabled);
      break;
    case "leave":
      showNotification("Partner disconnected. Finding new one...");
      resetConnection();
      break;
  }
};

// --- WebRTC Core Functions ---
async function ensurePeerConnection() {
  try {
    if (peerConnection && peerConnection.signalingState !== "closed") return;
    
    remoteIceCandidatesQueue = [];
    const turnConfig = await fetchTurnConfig();

    peerConnection = new RTCPeerConnection({
      iceServers: [
        { 
          urls: 'stun:stun.l.google.com:19302' 
        },
        {
          urls: "turn:global.turn.twilio.com:3478?transport=udp",
          username: turnConfig.username,
          credential: turnConfig.credential
        },
        {
          urls: "turn:global.turn.twilio.com:3478?transport=tcp",
          username: turnConfig.username,
          credential: turnConfig.credential
        },
        {
          urls: "turn:global.turn.twilio.com:443?transport=tcp",
          username: turnConfig.username,
          credential: turnConfig.credential
        }
      ]
    });

    configurePeerConnectionEventListeners();
  } catch (error) {
    console.error('Error ensuring peer connection:', error);
    throw error;
  }
}

function configurePeerConnectionEventListeners() {
  peerConnection.onicecandidate = (e) => {
    if (e.candidate) {
      ws.send(JSON.stringify({ type: "ice", candidate: e.candidate }));
    }
  };

  peerConnection.oniceconnectionstatechange = () => {
    const state = peerConnection.iceConnectionState;
    // console.log(`ICE State: ${state}`);

    switch (state) {
      case "checking":
        updateStatus("Establishing connection...", false);
        break;
      case "connected":
        updateStatus("", false);
        break;
      case "disconnected":
        console.log("Connection lost, attempting reconnection...");
        // Try immediate reconnection
        peerConnection.restartIce();
        break;
      case "failed":
        // Wait a bit before trying one last time
        setTimeout(() => {
          if (peerConnection.iceConnectionState === "failed") {
            createOfferWithIceRestart()
              .then(offer => {
                peerConnection.setLocalDescription(offer);
                ws.send(JSON.stringify({ type: "offer", offer: offer }));
            })
              .catch(() => {
                showNotification("Connection failed. Finding new partner...");
                resetConnection();
            });
          }
        }, 3000);
        break;
    }
  };

  // // Add connection quality monitoring
  // setInterval(() => {
  //   if (peerConnection?.getStats) {
  //     peerConnection.getStats().then(stats => {
  //       stats.forEach(report => {
  //         if (report.type === "candidate-pair" && report.state === "succeeded") {
  //           console.log("Connection Quality:", 
  //             report.availableOutgoingBitrate || 
  //             report.availableIncomingBitrate);
  //         }
  //       });
  //     });
  //   }
  // }, 3000);

  // peerConnection.onconnectionstatechange = () => {
  //   console.log(`Connection State: ${peerConnection.connectionState}`);
  // };

  // peerConnection.onicegatheringstatechange = () => {
  //   console.log(`ICE Gathering State: ${peerConnection.iceGatheringState}`);
  // };

  peerConnection.ontrack = (event) => {
    if (remoteVideo.srcObject !== event.streams[0]) {
      remoteVideo.srcObject = event.streams[0];
      updateStatus("", false);
      if (localStream) {
        sendMediaStatus("audio", localStream.getAudioTracks()[0]?.enabled);
        sendMediaStatus("video", localStream.getVideoTracks()[0]?.enabled);
      }
    }
  };
}

async function ensureLocalMediaAndTracks() {
  if (!localStream) await startMedia();
  if (localStream && peerConnection) {
    for (const track of localStream.getTracks()) {
      if (!peerConnection.getSenders().find((s) => s.track === track)) {
        peerConnection.addTrack(track, localStream);
      }
    }
  }
}

async function processQueuedIceCandidates() {
  // console.log(`Processing ${remoteIceCandidatesQueue.length} queued candidates`);
  const candidates = [...remoteIceCandidatesQueue];
  remoteIceCandidatesQueue = [];

  for (const candidate of candidates) {
    try {
      if (peerConnection.remoteDescription) {
        await peerConnection.addIceCandidate(new RTCIceCandidate(candidate));
        // console.log('Added queued ICE candidate', candidate.type);
      } else {
        remoteIceCandidatesQueue.push(candidate);
        console.log('Remote description not set, re-queuing candidate');
        break;
      }
    } catch (e) {
      console.error('Error adding queued candidate:', e);
    }
  }
}

function resetConnection() {
  if (peerConnection) {
    peerConnection.close();
    peerConnection = null;
  }
  remoteVideo.srcObject = null;
  remoteStatusContainer.innerHTML = "";
  setControlsEnabled(false);
  updateStatus("Searching for a new partner...");
  showControls();
  // --- AUTO-CONNECT ---
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ type: "ready_for_peer" }));
  }
}

// --- Media & Control Functions ---
async function startMedia() {
  try {
    localStream = await navigator.mediaDevices.getUserMedia({
      video: true,
      audio: true,
    });
    localVideo.srcObject = localStream;
  } catch (err) {
    updateStatus("Error: " + err.message);
    throw err;
  }
}

function sendMediaStatus(kind, enabled) {
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(
      JSON.stringify({ type: "media_status", kind: kind, enabled: enabled })
    );
  }
}

// --- Button Event Listeners ---
micBtn.addEventListener("click", () => {
  const audioTrack = localStream?.getAudioTracks()[0];
  const audioSender = peerConnection
    ?.getSenders()
    .find((sender) => sender.track?.kind === "audio");

  if (!audioTrack || !audioSender?.track) {
    console.warn("Audio track not found");
    return;
  }

  // Toggle audio state for both local and remote tracks
  const isEnabled = !audioTrack.enabled;
  audioTrack.enabled = isEnabled;
  audioSender.track.enabled = isEnabled;

  // Update button appearance
  micBtn.classList.toggle("active", isEnabled);
  micBtn.innerHTML = isEnabled
    ? '<i class="fa-solid fa-microphone"></i>'
    : '<i class="fa-solid fa-microphone-slash"></i>';

  // Notify peer about audio state change
  sendMediaStatus("audio", isEnabled);
});

videoBtn.addEventListener("click", () => {
  const videoTrack = localStream?.getVideoTracks()[0];
  const videoSender = peerConnection
    ?.getSenders()
    .find((sender) => sender.track?.kind === "video");

  if (!videoTrack || !videoSender?.track) {
    console.warn("Video track not found");
    return;
  }

  // Toggle video state for both local and remote tracks
  const isEnabled = !videoTrack.enabled;
  videoTrack.enabled = isEnabled;
  videoSender.track.enabled = isEnabled;

  // Update button appearance
  videoBtn.classList.toggle("active", isEnabled);
  videoBtn.innerHTML = isEnabled
    ? '<i class="fa-solid fa-video"></i>'
    : '<i class="fa-solid fa-video-slash"></i>';

  // Keep video element visible but show black frame when disabled
  localVideo.style.display = "block";
  if (!isEnabled) {
    localVideo.classList.add("video-off");
  } else {
    localVideo.classList.remove("video-off");
  }

  // Notify peer about video state change
  sendMediaStatus("video", isEnabled);
});

selfViewBtn.addEventListener("click", () => {
  const videoTrack = localStream?.getVideoTracks()[0];
  if (!videoTrack?.enabled) {
    showNotification("Turn on your camera first");
    return;
  }

  localVideo.classList.toggle("hidden");

  selfViewBtn.classList.toggle(
    "active",
    !localVideo.classList.contains("hidden")
  );
  selfViewBtn.innerHTML = localVideo.classList.contains("hidden")
    ? '<i class="fa-solid fa-eye-slash"></i>'
    : '<i class="fa-solid fa-eye"></i>';
});

fullscreenBtn.addEventListener("click", () => {
  if (!document.fullscreenElement) document.documentElement.requestFullscreen();
  else document.exitFullscreen();
});
disconnectBtn.addEventListener("click", () => {
  confirmDisconnectModal.classList.remove("hidden");
});
confirmDisconnectCancelBtn.addEventListener("click", () => {
  confirmDisconnectModal.classList.add("hidden");
});
confirmDisconnectOkBtn.addEventListener("click", () => {
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ type: "leave" }));
  }
  resetConnection();
  confirmDisconnectModal.classList.add("hidden");
});

// --- Initial Page Load ---
async function initialize() {
  try {
    // Fetch TURN config in parallel with media access
    const [_, mediaResult] = await Promise.all([
      fetchTurnConfig(),
      startMedia()
    ]);
    showControls();
  } catch (e) {
    console.error(`Initialization failed:`, e);
  }
}

initialize();


async function createOfferWithIceRestart() {
    try {
        const offer = await peerConnection.createOffer({
            iceRestart: true,
            offerToReceiveAudio: true,
            offerToReceiveVideo: true
        });
        return offer;
    } catch (error) {
        console.error('Error creating offer:', error);
        throw error;
    }
}

// function to fetch TURN config once
async function fetchTurnConfig() {
    if (cachedTurnConfig) return cachedTurnConfig;
    
    try {
        const response = await fetch("/api/turn-config");
        cachedTurnConfig = await response.json();
        return cachedTurnConfig;
    } catch (e) {
        console.warn('Failed to fetch TURN config:', e);
        return {};
    }
}