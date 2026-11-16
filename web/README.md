# MEC Connect website

A responsive, browser-based web version of the MEC Connect campus companion. It includes a dashboard, channel-based chat, event listings and RSVPs, study-resource sharing and search, a personal schedule, profile settings, dark mode, and JSON backup/export.

## Run locally

Open `web/index.html` in a browser, or serve the repository root with any static file server. No build step or npm install is required.

## Deploy to Vercel

The repository root contains `vercel.json`, which configures Vercel to publish the `web/` static site. If this repository is connected to Vercel, pushing to `main` should trigger a deployment; otherwise import `Aadinathap/Mec-Kochi-ChatGPT` in Vercel and deploy it. The site should then be available at the Vercel project domain (the repository currently lists `https://mec-kochi-chatgpt.vercel.app`).

## Data and backend status

This version intentionally runs without credentials or a backend. Data is saved in the current browser using local storage; it is not shared across browsers or users and is not connected to Firebase. Use **My profile → Download my data** to export a backup. Sample content is illustrative and should be replaced with verified campus information.

To make this a shared, multi-user campus service, a web Firebase app configuration and Firebase Authentication / Firestore integration still need to be set up and tested. Never put service-account credentials or private keys in browser code.
