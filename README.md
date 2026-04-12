# 🚀 SkySentry

> **AI-Powered Visual Verification Layer for Aerial Threat Detection**

---

<p align="center">
  <img src="https://img.shields.io/badge/Status-Active-success?style=for-the-badge"/>
  <img src="https://img.shields.io/badge/Backend-SpringBoot-orange?style=for-the-badge"/>
  <img src="https://img.shields.io/badge/AI-YOLOv10-blue?style=for-the-badge"/>
  <img src="https://img.shields.io/badge/Frontend-JavaScript-black?style=for-the-badge"/>
</p>

---

## 🧠 Overview

SkySentry AI is a **real-time aerial threat verification system** designed to bridge the gap between raw radar detection and actionable intelligence.

Traditional radar systems struggle with:

* ❌ False positives (birds, clutter)
* ❌ Lack of classification
* ❌ Alert fatigue

### ✅ Our Solution

SkySentry introduces a **Visual Verification Layer**:

> Radar Detection → Camera Feed → AI Classification → Threat Decision

---

## ⚙️ System Pipeline

```text
Radar Trigger (Simulated)
        ↓
Camera Feed (Image / Video)
        ↓
YOLOv10 Model (ONNX Runtime)
        ↓
Object Classification
        ↓
Threat Analysis
        ↓
Command Center UI
```

---

## 🔥 Key Features

* 🎯 **Precision Classification**
  Detects drones, birds, and aircraft with confidence scoring

* ⚡ **Real-Time Processing**
  Optimized for low-latency inference

* 🧠 **AI-Powered Decision Layer**
  Converts detections into actionable threat insights

* 🖥️ **Command Center UI**
  Military-style dashboard for monitoring and control

* 🔗 **Modular Architecture**
  Easily integrates with radar, EO, or IR systems

---

## 🧰 Tech Stack

### 🚀 Backend

* ☕ Spring Boot (Java)
* ONNX Runtime (Model Inference)

### 🤖 AI / ML

* YOLOv10
* PyTorch → ONNX Conversion

### 🎨 Frontend

* HTML5
* CSS3 (HUD-style UI)
* JavaScript

---

## 📁 Project Structure

```bash
SkySentry/
│
├── backend-spring/     # Spring Boot backend
├── index.html          # Frontend dashboard
├── best.onnx           # Model for inference
├── best.pt             # Training model (reference)
├── .gitignore
└── README.md
```

---

## 🚀 Getting Started

### 🔧 Backend Setup

```bash
cd backend-spring
mvn clean install
mvn spring-boot:run
```

Backend runs at:

```
http://localhost:8080
```

---

### 🌐 Frontend Setup

Simply open:

```bash
index.html
```

in your browser

---

## 🔌 API Endpoint

### Analyze Frame

```
POST /analyze
```

**Input:**

* Image / Base64 Frame

**Output:**

```json
{
  "detections": [
    {
      "class": "drone",
      "confidence": 0.98,
      "bbox": [x1, y1, x2, y2]
    }
  ]
}
```

---

## 🧪 Model Workflow

```text
Train Model → best.pt
        ↓
Convert to ONNX
        ↓
Deploy best.onnx
```

---

## 🎯 Use Cases

* 🛡️ Defense Surveillance
* 🏟️ Stadium Security
* 🏭 Industrial Monitoring
* 🚁 Drone Detection Systems

---

## ⚠️ Important Notes

* ONNX model is required for backend inference
* Ensure correct file path for `best.onnx`
* Backend must be running before frontend interaction

---

## 👥 Team

* **Pranjal** — Frontend & System Design
* **Savyam** — Backend & AI Integration
* **Aryan** — Research & Strategy
* **Naitik** — Presentation & Design
* **Vaibhavi** — Data pipeline 

---

## 💡 Vision

> Democratizing high-fidelity aerial defense systems using accessible AI and modern architectures.

---

<p align="center">
  ⭐ If you like this project, consider giving it a star!
</p>
