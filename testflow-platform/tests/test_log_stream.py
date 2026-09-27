import queue

from app.log_stream import RunLogStream


def test_publish_subscribe_and_unsubscribe():
    stream = RunLogStream()
    channel = stream.subscribe(42)

    stream.publish(42, "pytest started\n")

    assert channel.get_nowait() == "pytest started\n"
    stream.unsubscribe(42, channel)
    stream.publish(42, "ignored\n")
    try:
        channel.get_nowait()
        assert False, "unsubscribed channel should not receive new logs"
    except queue.Empty:
        pass
