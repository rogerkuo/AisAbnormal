AisAbnormal Event Analyzer Application
===========

## Configure ##
To configure the application:

* On MacOS and Linux
    * Edit the `run-analyzer.sh` file to configure application settings.
* On Windows
    * Edit the `run-analyzer.bat` file to configure application settings.

## Run ##
The run the application:

* On MacOS and Linux
    * Execute `run-analyzer.sh`.
* On Windows
    * Execute `run-analyzer.bat`.
* Via Docker
    * Execute `sudo docker run -e CONFIG_FILE=/data/analyzer.properties -v ~/tmp/data:/data dmadk/ais-ab-analyzer:latest`
    