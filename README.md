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

This branch is focused on compatibility for legacy clients from `0.12.x` up to `1.1.x`:

* Based on the old Nukkit / NK implementation, keeping the historical behavior and plugin ecosystem as much as possible
* Focused on multi-version compatibility for legacy clients from `0.12.x` to `1.1.x`
* Does **not** target `1.2+` protocol support in this branch

Status
-------------

This project currently targets the legacy protocol / version range below:

* `0.12.0` - `0.12.3`
* `0.13.0` - `0.13.2`
* `0.14.0` - `0.14.3`
* `0.15.0` - `0.15.10`
* `0.16.0`
* `1.0.0.0` - `1.0.9`
* `1.1.0` - `1.1.7`

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
Contributions related to legacy compatibility, regression fixes, and `0.13.x` to `1.1.x` protocol support are welcome.

Discussion
-------------
Please use the repository issue tracker and pull requests for discussion and collaboration.
