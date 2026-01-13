# Gemini Bicycle Head Unit Android App

This document outlines the steps to build a bicycle head unit Android app using the Gemini CLI.

## Project Overview

The goal is to create a simple yet powerful Android app that functions as a bicycle head unit. The app will provide real-time tracking of key metrics and connect to external sensors for enhanced safety and performance monitoring.

## Core Features

*   **Real-time Metrics:**
    *   Speed (current, average, max)
    *   Elevation (current, gain, loss)
    *   Incline (current grade)
    *   Elapsed time
    *   Distance traveled
*   **External Sensor Connectivity:**
    *   Bluetooth Low Energy (BLE) for heart-rate sensors.
    *   ANT+ or BLE for car-detecting radar units (e.g., Garmin Varia).

## Development Plan

The development will be broken down into the following stages:

1.  **Project Setup:**
    *   Initialize a new Android project using Kotlin and Jetpack Compose.
    *   Set up the necessary dependencies for GPS, Bluetooth, and potentially ANT+.

2.  **UI/UX Design:**
    *   Design a clean and easy-to-read main screen that displays all the key metrics.
    *   The UI should be optimized for outdoor visibility.

3.  **GPS and Location Services:**
    *   Implement logic to access the device's GPS data.
    *   Calculate speed, distance, and elevation from the GPS data.
    *   Implement a persistent service to track location in the background.

4.  **Sensor Integration:**
    *   Implement BLE scanning and connection for heart-rate sensors.
    *   Research and implement connectivity for a car-detecting radar unit. This may involve using a specific SDK or reverse-engineering the protocol if no public API is available.

5.  **Data Management:**
    *   Implement a data model to store ride data.
    *   Use a local database (e.g., Room) to save ride history.

6.  **Testing and Refinement:**
    *   Write unit tests for the business logic (e.g., calculations).
    *   Conduct on-device testing to ensure accuracy and stability.
    *   Refine the UI/UX based on testing feedback.

## Getting Started

To start the project, follow these steps:

1.  Create a new Android project.
2.  Implement the UI for the main screen.
3.  Integrate GPS and display basic metrics.
