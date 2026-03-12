Nukkit-Retro
===================
![nukkit](/images/banner.png)

	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU Lesser General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU Lesser General Public License for more details.

	You should have received a copy of the GNU Lesser General Public License
	along with this program.  If not, see <http://www.gnu.org/licenses/>.


__A Retro Nukkit Fork Focused On Legacy Minecraft: Pocket Edition Compatibility__

Introduction
-------------

Nukkit-Retro is a Nukkit fork based on the old NK codebase, maintained for legacy Minecraft: Pocket Edition support.

This branch is focused on compatibility for `1.1.x` and below:

* Based on the old Nukkit / NK implementation, keeping the historical behavior and plugin ecosystem as much as possible
* Focused on multi-version compatibility for `1.1.x` and earlier legacy clients
* Does **not** target `1.2+` protocol support in this branch

Status
-------------

This project currently targets the legacy protocol range below:

* `0.15.x`
* `0.16.0`
* `1.0.x`
* `1.1.x`

If you need modern Bedrock support, this repository is not intended for that use case.

Build JAR file
-------------
- `mvn clean`
- `mvn package`

Running
-------------
Example:

```bash
java -jar nukkit-retro-SNAPSHOT.jar
```

Plugin API
-------------
#### **Example Plugin**
Nukkit-Retro keeps the original Nukkit package structure and targets legacy plugin compatibility where possible.

* __[Example Plugin](http://github.com/Nukkit/ExamplePlugin)__

Contributing
------------
Contributions related to legacy compatibility, regression fixes, and `1.1.x` and below protocol support are welcome.

Discussion
-------------
Please use the repository issue tracker and pull requests for discussion and collaboration.
