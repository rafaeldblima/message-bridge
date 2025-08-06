#!/usr/bin/env python3

import pika
import json
import time
import os
import sys
from datetime import datetime

class MessagePublisher:
    def __init__(self):
        self.host = os.getenv('RABBITMQ_HOST', 'localhost')
        self.port = int(os.getenv('RABBITMQ_PORT', '5672'))
        self.username = os.getenv('RABBITMQ_USER', 'admin')
        self.password = os.getenv('RABBITMQ_PASSWORD', 'admin123')
        self.vhost = os.getenv('RABBITMQ_VHOST', '/')
        
        self.connection = None
        self.channel = None

    def connect(self):
        credentials = pika.PlainCredentials(self.username, self.password)
        parameters = pika.ConnectionParameters(
            host=self.host,
            port=self.port,
            virtual_host=self.vhost,
            credentials=credentials
        )
        
        try:
            self.connection = pika.BlockingConnection(parameters)
            self.channel = self.connection.channel()
            print(f"Connected to RabbitMQ at {self.host}:{self.port}")
            return True
        except Exception as e:
            print(f"Failed to connect to RabbitMQ: {e}")
            return False

    def publish_message(self, queue_name, message):
        if not self.channel:
            print("Not connected to RabbitMQ")
            return False

        try:
            # Ensure the queue exists
            self.channel.queue_declare(queue=queue_name, durable=True)
            
            # Publish the message
            self.channel.basic_publish(
                exchange=queue_name,
                routing_key=queue_name,
                body=json.dumps(message),
                properties=pika.BasicProperties(
                    delivery_mode=2,  # Make message persistent
                    content_type='application/json',
                    timestamp=int(time.time())
                )
            )
            
            print(f"Published message to {queue_name}: {message}")
            return True
        except Exception as e:
            print(f"Failed to publish message to {queue_name}: {e}")
            return False

    def close(self):
        if self.connection and not self.connection.is_closed:
            self.connection.close()
            print("Disconnected from RabbitMQ")

def main():
    publisher = MessagePublisher()
    publisher_name = os.getenv('PUBLISHER_NAME', 'Message Publisher')
    
    if not publisher.connect():
        sys.exit(1)
    
    try:
        print(f"{publisher_name} Started")
        print(f"Connected to RabbitMQ at {publisher.host}:{publisher.port}")
        print("Available commands:")
        print("  msg <message> - Send message to this instance's queue")
        print("  auto - Send automatic test messages")
        print("  quit - Exit")
        
        while True:
            try:
                command = input("\n> ").strip()
                
                if command == "quit":
                    break
                elif command == "auto":
                    # Use the single source queue
                    queue = "source.queue"
                    source_name = "source"
                    
                    # Send automatic test messages
                    messages = [
                        {"id": 1, "content": f"Auto test message from {source_name}", "timestamp": datetime.now().isoformat()},
                        {"id": 2, "content": f"Another auto message from {source_name}", "data": {"type": "test", "value": 123}},
                        {"id": 3, "content": f"JSON message from {source_name}", "nested": {"field": "value", "number": 42}}
                    ]
                    
                    for msg in messages:
                        publisher.publish_message(queue, msg)
                        time.sleep(1)
                
                elif command.startswith("msg "):
                    message_text = command[4:]
                    
                    # Use the single source queue
                    queue = "source.queue"
                    
                    message = {
                        "content": message_text,
                        "timestamp": datetime.now().isoformat(),
                        "source": "manual-input",
                        "publisher": publisher_name
                    }
                    publisher.publish_message(queue, message)
                
                else:
                    print("Invalid command. Use 'msg <message>', 'auto', or 'quit'")
            
            except KeyboardInterrupt:
                print("\nReceived interrupt signal")
                break
            except EOFError:
                print("\nEOF received")
                break
    
    finally:
        publisher.close()

if __name__ == "__main__":
    main()