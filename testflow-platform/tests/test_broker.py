from app.broker import LocalTaskBroker


def test_local_broker_is_fifo():
    broker = LocalTaskBroker()
    broker.enqueue(11)
    broker.enqueue(12)

    assert broker.dequeue(timeout=0) == 11
    assert broker.dequeue(timeout=0) == 12
    assert broker.dequeue(timeout=0) is None

