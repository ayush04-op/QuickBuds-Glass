# QuickBuds-Glass

An enhanced, customized fork of the open-source **QuickBuds** Android application featuring a modern **Liquid Glass UI**, designed to provide an immersive and sleek user experience.

## Overview

QuickBuds-Glass is built upon the core architecture of QuickBuds, bringing modern aesthetic enhancements—such as a custom Liquid Glass interface—while maintaining core functionalities and performance. This project leverages automated GitHub Actions workflows for continuous integration and seamless debug APK building.

## Features

* **Liquid Glass UI**: Integrates custom visual helpers and styling (`LiquidGlassHelper.kt`) to deliver a translucent, modern UI design language.
* **Side-by-Side Installation Support**: Configured with custom package renaming capabilities, allowing it to be installed alongside the official release version without conflicts.
* **Automated CI/CD Workflows**: Streamlined GitHub Actions pipelines configured for JDK 21 to automatically compile, test, and package debug APKs.

## Tech Stack & Requirements

* **Language**: Kotlin & Java
* **Framework**: Android SDK
* **Build System**: Gradle
* **Required JDK**: JDK 21 (as specified in the repository build documentation)

## Building and Development

To build the project locally or via custom workflows:

1. Clone the repository and check out your fork:
   ```bash
   git clone [https://github.com/ayush04-op/QuickBuds-Glass.git](https://github.com/ayush04-op/QuickBuds-Glass.git)
   cd QuickBuds-Glass
