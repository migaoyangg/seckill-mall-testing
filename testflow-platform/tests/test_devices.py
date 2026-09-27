from app.devices import parse_adb_devices


def test_parse_adb_devices_with_online_and_offline_devices():
    output = """List of devices attached
emulator-5554 device product:sdk_gphone64_arm64 model:sdk_gphone64_arm64 transport_id:1
R58M123 unauthorized usb:1-1 transport_id:2
offline-1 offline transport_id:3
"""

    devices = parse_adb_devices(output)

    assert devices[0] == {
        "serial": "emulator-5554",
        "status": "ONLINE",
        "model": "sdk_gphone64_arm64",
        "product": "sdk_gphone64_arm64",
    }
    assert devices[1]["status"] == "UNAUTHORIZED"
    assert devices[2]["status"] == "OFFLINE"

