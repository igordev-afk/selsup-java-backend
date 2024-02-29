package ru.wwerlosh.task;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) {

        DocumentDTO documentDTO = makeDocumentDTO();
        String signature = "98SajsISA9sa98";
        CrptApi api = new CrptApi(5, Duration.ofSeconds(5));

        ExecutorService executor = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 100; i++) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            executor.execute(() -> api.createDocument(documentDTO, signature));

//            api.createDocument(documentDTO, signature);
        }

        executor.shutdown();
    }

    private static DocumentDTO makeDocumentDTO() {
        ProductDTO product = new ProductDTO.Builder()
                .certificateDocument("Doc1")
                .certificateDocumentDate("2024-02-29")
                .certificateDocumentNumber("ABC123")
                .ownerInn("1234567890")
                .producerInn("0987654321")
                .productionDate("2024-02-29")
                .tnvedCode("123456")
                .uitCode("7890")
                .uituCode("9876")
                .build();
        List<ProductDTO> products = new ArrayList<>();
        products.add(product);

        DocumentDTO documentDTO = new DocumentDTO.Builder()
                .docId("1")
                .docType("Type")
                .docStatus("Status")
                .importRequest(true)
                .ownerInn("OwnerINN")
                .participantInn("ParticipantINN")
                .producerInn("ProducerINN")
                .productionDate("ProductionDate")
                .productionType("ProductionType")
                .regDate("RegDate")
                .regNumber("RegNumber")
                .products(products)
                .build();

        return documentDTO;
    }
}
