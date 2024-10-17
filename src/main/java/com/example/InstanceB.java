package com.example;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.List;

public class InstanceB {

    public static void main(String[] args) {
        String queueUrl = "https://sqs.us-east-1.amazonaws.com/610111708296/imageindexes";
        String bucketName = "njit-cs-643";  // S3 bucket name
        Region region = Region.US_EAST_1;   // Region where your resources are

        // Initialize AWS Clients
        SqsClient sqsClient = SqsClient.builder()
                .region(region)
                .credentialsProvider(ProfileCredentialsProvider.create())
                .build();

        RekognitionClient rekClient = RekognitionClient.builder()
                .region(region)
                .credentialsProvider(ProfileCredentialsProvider.create())
                .build();

        // Continuously poll for messages from SQS
        while (true) {
            receiveMessages(sqsClient, bucketName, rekClient, queueUrl);
        }
    }

    public static void receiveMessages(SqsClient sqsClient, String bucketName, RekognitionClient rekClient, String queueUrl) {
        // Set up long polling to receive messages from SQS
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10) // Max messages to receive at once
                .waitTimeSeconds(20)     // Long polling for 20 seconds
                .build();

        ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
        List<Message> messages = response.messages();

        // Process each message
        for (Message message : messages) {
            String imageKey = message.body();

            // Check for termination signal (-1)
            if (imageKey.equals("-1")) {
                System.out.println("Received termination signal. No more messages to process.");
                return;  // Stop processing further messages
            }

            System.out.println("Processing image key: " + imageKey);

            // Call method to perform text recognition
            performTextRecognition(rekClient, bucketName, imageKey);

            // Delete the message from the queue after processing
            sqsClient.deleteMessage(r -> r.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
            System.out.println("Deleted message: " + message.messageId());
        }
    }

    public static void performTextRecognition(RekognitionClient rekClient, String bucketName, String imageKey) {
        try {
            // Create the Image object for Rekognition
            Image image = Image.builder()
                    .s3Object(software.amazon.awssdk.services.rekognition.model.S3Object.builder()
                            .bucket(bucketName)  // Correct usage of bucket
                            .name(imageKey)      // Correct usage of imageKey
                            .build())
                    .build();

            // Create the request for text detection
            DetectTextRequest detectTextRequest = DetectTextRequest.builder()
                    .image(image)
                    .build();

            // Perform the text detection
            DetectTextResponse detectTextResponse = rekClient.detectText(detectTextRequest);
            List<TextDetection> detectedTexts = detectTextResponse.textDetections();

            // Output detected text
            for (TextDetection text : detectedTexts) {
                System.out.println("Detected text: " + text.detectedText() + " (Confidence: " + text.confidence() + ")");
            }

        } catch (RekognitionException e) {
            System.err.println("Text recognition failed: " + e.getMessage());
        }
    }
}
