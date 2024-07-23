# SNATS: Scala NATS Message Broker

SNATS is a lightweight, message broker implemented in Scala. Inspired from https://codingchallenges.fyi/challenges/challenge-nats/.

## Features

- **Pub-Sub Messaging**: Easily publish and subscribe to topics
- **Health Checks**: Built-in ping command to verify connection status
- **Topic Management**: Support for subscribing and unsubscribing from topics
- **Scala Implementation**: Leverages Scala's concurrency and functional programming features

## Commands Supported

SNATS currently supports the following commands:

1. `PUB`: Publish a message to a topic
2. `SUB`: Subscribe to a topic
3. `UNSUB`: Unsubscribe from a topic
4. `PING`: Perform a health check
