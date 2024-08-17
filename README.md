# Better Internet Tiles

<img alt="GitHub" src="https://img.shields.io/github/license/casperverswijvelt/better-internet-tiles">

## About

This application aims to create a unified internet quick-settings tile, which is actually useful (I'm looking at you, Android 12). Next to this, **separate Wi-Fi and mobile data tiles are also available** if you just want to go back to the behaviour of Android 11 or lower.

Tapping the new unified internet tile will simply toggle between Wi-Fi and mobile data, which is exactly what I want it to do most of the time. This reduces the amount of taps needed from 3 (tap tile, disable wifi, enable data) to just 1 quick tap. In situations where you still want more control, long pressing the tile will redirect you to the relevant settings page.

**This has been tested and confirmed working on Pixel devices running Android 12 and 13, but other devices will probably work too.**

## Shell access required
Shell access is required to enable/disable Wi-Fi and mobile data, as well as for reading the SSID of the current Wi-Fi network. This can be granted using ROOT.

## Features
- An improved unified Internet tile where you can tap to toggle between Wi-Fi and mobile data (visually very similar to the stock Android 12 tile, but more functional)
- Separate Wi-Fi and mobile data tiles if you just want to go back to behaviour before Android 12
- NFC tile which was apparently also removed
- Ability to configure access to the tiles while the phone is locked

## How to install
- Download and install the app.
- Open the app, and click on the 'Request ROOT access' button.
- Edit your quicksettings layout, drag your desired tile to the top and remove the original internet tile.
If you are on Android 13 or higher, you can also add them using the shortcut buttons within the app.
- Enjoy easier switching between mobile data and WiFi with just a single tap!
