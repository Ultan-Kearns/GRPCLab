package ie.gmit.ds;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.protobuf.BoolValue;
import com.google.protobuf.Empty;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

public class InventoryClient {
	private static final Logger logger =
            Logger.getLogger(InventoryClient.class.getName());
    private final ManagedChannel channel;
    private final InventoryServiceGrpc.InventoryServiceStub asyncInventoryService;
    private final InventoryServiceGrpc.InventoryServiceBlockingStub syncInventoryService;

    public InventoryClient(String host, int port) {
        channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
        syncInventoryService = InventoryServiceGrpc.newBlockingStub(channel);
        asyncInventoryService = InventoryServiceGrpc.newStub(channel);
    }
    
    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    public void addNewInventoryItem(Item newItem) {
        logger.info("Adding new inventory item " + newItem);
        BoolValue result = BoolValue.newBuilder().setValue(false).build();
        try {
            result = syncInventoryService.addItem(newItem);
        } catch (StatusRuntimeException ex) {
            logger.log(Level.WARNING, "RPC failed: {0}", ex.getStatus());
            return;
        }
        if (result.getValue()) {
            logger.info("Successfully added item " + newItem);
        } else {
            logger.warning("Failed to add item");
        }
    }
    private void getItems() {
        StreamObserver<Items> responseObserver = new StreamObserver<Items>() {
            @Override
            public void onNext(Items items) {
                logger.info("Received items: " + items);
            }

            @Override
            public void onError(Throwable throwable) {
                Status status = Status.fromThrowable(throwable);

                logger.log(Level.WARNING, "RPC Error: {0}", status);
            }

            @Override
            public void onCompleted() {
                logger.info("Finished receiving items");
                // End program
                System.exit(0);
            }
        };

        try {
            logger.info("Requesting all items ");
            asyncInventoryService.getItems(Empty.newBuilder().build(), responseObserver);
            logger.info("Returned from requesting all items ");
        } catch (
                StatusRuntimeException ex) {
            logger.log(Level.WARNING, "RPC failed: {0}", ex.getStatus());
            return;
        }
    }
    public static void main(String[] args) throws Exception {
        InventoryClient client = new InventoryClient("localhost", 50551);
        Item newItem = Item.newBuilder()
                .setId("1234")
                .setName("New Item")
                .setDescription("Best New Item")
                .build();
        try {
            client.addNewInventoryItem(newItem);
            client.getItems();
        } finally {
            // Don't stop process, keep alive to receive async response
            Thread.currentThread().join();
        }
    }
    
}
