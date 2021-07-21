## Release Notes :

#### 1.25.1:

Fix Jenkins Plugin:
* Fixed an issue where node name was not used when creating new application

#### 1.25.0:

TODO

#### 1.20.0

This version contains compatibility fixes for AIP Console 1.20.0

Fix:
* Fixed response parsing due to changes from Jobs endpoint response

Feature:
* Some information will no longer be shown in the logs
* When interrupting the CLI (with CTRL+C) or aborting a Jenkins Build during a job (**except** during application creation), the job will be cancelled in AIP Console as well.

#### 1.19.0

This version contains compatibility fixes for AIP Console 1.19.0

Feature:

* Added domain parameter when creating an application (and with auto create for Add and Deliver)

#### 1.18.1

Fix
* Use sysdate as version name when name is empty in jenkins plugin

#### 1.18.0

Build
* Add profile delivery_manager to build the jenkins plugin with only deliver source code option

Global
* Print detailed logs for each step in realtime both in jenkins and CLI console
* Deliver step, download the delivery report to workspace when delivery done in jenkins
* Deliver step, add Exclusion patterns configuration
* Deliver step, add Automatic discovery configuration, enabled by default
* Deliver step, rename the "Rescan" option to "Copy configuration from previous version", put to advanced settings, and set enabled by default

#### 1.17.0

This version provides new commands in the CLI and new Steps in the Jenkins Plugin.

Fixes (Global):

* Fixed compatibility issue with AIP Console 1.12
* Changed Snapshot Name to be coherent in both CLI and Jenkins Plugin

CLI:

* Added the Deliver, Analysis and Snapshot commands
* Reviewed the documentation to add those commands and provide clearer information of each commands parameters.
* Fixed an issue in Analyze Command where Snapshot Steps where not enabled even though the snapshot parameter was passed
* Fixed an issue where the Version Name for snapshot was not properly send to AIP Console

Jenkins Plugin

* Added the Deliver, Analyze and Snapshot Build Steps
* Reviewed the documentation for those Build Steps
* Fixed an issue in Analyze Build Step where snapshot steps were not enabled even though the "Snapshots?" checkbox was checked
* Fixed an issue in Analyze Build Step where the Version Name was not properly saved

#### 1.16.1

Fixed an issue with file upload when package path check is disabled in AIP Console

#### 1.16.0

Added support for AIP Console 1.16.0

Added option to backup the application before adding a new version (requires AIP Console 1.16.0 or above)

#### 1.15.0

As of this release, we're aligning the version number with AIP Console.

Added Source Folder support (requires AIP Console 1.15.0 or above)

#### 1.1.3

Added compatibility with version 1.15.x of AIP Console 

#### 1.1.2

Compatibility with the latest version of AIP Console (1.13.0) :
* Check AIP Console version
* Upload and Extract content in ChunkUploadService if AIP Console has `enablePackagePath` option enabled
* Changed Job Parameters if AIP Console has `enablePackagePath` option enabled

#### 1.1.1

Various fixes for Jenkins Plugin and CLI :

Fix :
* Reworked app version parsing
* Incorrect log level for amount of data read from file
* Always get the application guid from AIP Console
* fixed logger for CLI not showing information messages ABR

#### 1.1.0

List of added features :
* You can specify a node name when creating a new application
* When using the "Rescan" parameter, if no version exists for the application, the version creation will not fail anymore but just launch an Add Version Job
* Versions will now be created with Version Objectives by default : Global Risks, Functional Points.
* You can also check the "Enable Security Dataflow" to add the Version Objective "Security" and enable analysis with a Security Dataflow.

Jenkins Plugin 
* Rolled back the behaviour of 1.0.6 : the files are not renamed, but the name inside AIP Console will be randomized instead, leaving your source zip file as is.
* ⚠ Some parameter are now hidden behind the "Advanced Settings" button in the "Add Version" step. 

CLI
* Send a randomized name to AIP Console for the ZIP file with the same content as the provided file.

Fix:
* In some cases, chunks uploaded to AIP Console were much smaller than the 10 MB fixed chunk size. The upload will now fill the buffer with 10MB (unless it reaches the end of the file) before sending it to AIP Console.
    * The size of the chunk will remain fixed for now, but will be parameterized in a later version.
* Updated library `com.fasterxml.jackson.core` libraries to version 2.10.0, to fix a security vulnerability

#### 1.0.6

Files to be uploaded are renamed prior to upload. This is to avoid an issue with file name overlapping in AIP Console.

#### 1.0.5 :

Minor release with changes to the Java package in the CLI and Core lib.
Updates to the documentation.

Added an example for the Jenkins Plugin use in a Jenkins Pipeline Job

#### 1.0.4 : 

⚠ Default request timeout is now 90s. This is to avoid errors when uploading very large files (over 1GB). 

Fix :
* An error with the message `UploadException: No more content to read, but file not complete (the file might be modified by another program?).` occured for evry large file. This was due to an integer overflow that has been fixed.
* Updated library `com.fasterxml.jackson.core` libraries to version 2.9.10, to fix a vulnerability (see https://nvd.nist.gov/vuln/detail/CVE-2019-14540 and https://nvd.nist.gov/vuln/detail/CVE-2019-16335)

#### 1.0.3:

Aligning version with CAST Extend version.
 
Additions : Connection Timeout, Rescan, Application Name in Add Version, Automatically create a new application if the give name doesn't exist, Ignore Failures

CLI:
* With the `AddVersion` sub command, you can specify the application name (with `-n`) instead of having to provide an application guid
* With the `AddVersion` sub command, you can specify `--auto-create` to create a new application on the target AIP Console instance if it doesn't exists
* You can specify a timeout (in seconds) for each sub commands, in case your AIP Console instance takes some time to answer.

Jenkins Plugin:
* In the Add Version build step, you have to specify an application name, instead of the application GUID.
* In the Add Version build step, you can check the "auto create" checkbox to have AIP Console plugin automatically create the application in AIP Console instance.
* In the Add Version build step, you can check "Rescan" to automate the delivery and analysis of your application. This will create a new version, based on the previously delivered one, and keep the same configuration.
* In the Add Version build step, you can check "Ignore Failures" to avoid having the plugin fail the job. Instead, the plugin will mark its execution has being unstable if any issue occurs.

*Note : Application GUID will still appear in the build step, but it is a read only value, meant for verification (and backwards compatibility)*

#### 0.0.1 :

Initial release 🎉 

CLI
* Create an application using the `CreateApplication` subcommand
* Deliver a new version using the `AddVersion` subcommand

Jenkins Plugin :
* Configure access to your AIP Console instance in the Global Configuration of Jenkins
* Create a new application within a Jenkins Job, either Freestyle or Pipeline
* Add a new version to an existing application on AIP Console in a Jenkins Job 