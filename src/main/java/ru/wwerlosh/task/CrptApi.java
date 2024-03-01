package ru.wwerlosh.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class CrptApi {

    private final CrptApiHttpClient crptApiHttpClient;
    private final RateLimiter crptApiRateLimiter;
    private final ObjectSerializer documentSerializer;

    public CrptApi(int requestTime, Duration duration) {

        Objects.requireNonNull(duration, "Duration cannot be null");

        if (requestTime <= 0) {
            throw new IllegalArgumentException("Request time must be positive");
        }

        this.crptApiHttpClient = new CrptApiHttpClient("https://ismp.crpt.ru/api/v3/lk/documents/create");
        this.crptApiRateLimiter = new SemaphoreRateLimiter(requestTime, duration);
        this.documentSerializer = new DocumentSerializer();
    }

    public void createDocument(DocumentDTO document, String signature) {
        crptApiRateLimiter.acquire();

        String documentJson = documentSerializer.serialize(document);
        crptApiHttpClient.post(documentJson, signature);
    }
}

class CrptApiHttpClient {

    private final CloseableHttpClient httpClient;
    private final String API_URI;

    public CrptApiHttpClient(String URI) {
        this.httpClient = HttpClients.createDefault();
        this.API_URI = URI;
    }

    public void post(String requestBody, String token) {
        HttpPost request = new HttpPost();
        setAuthorizationHeader(request, token);
        setBody(request, requestBody);
        setURI(request);

        execute(request);
    }

    private void setAuthorizationHeader(HttpPost request, String token) {
        request.setHeader("Authorization", token);
    }

    private void setBody(HttpPost request, String requestBody) {
        try {
            StringEntity entity = new StringEntity(requestBody);
            request.setEntity(entity);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private void setURI(HttpPost request) {
        final URI uri = URI.create(API_URI);
        request.setURI(uri);
    }

    private void execute(HttpRequestBase request) {
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            StatusCodeHandler.handleStatusCode(statusCode);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class StatusCodeHandler {

        private static final Map<Integer, String> STATUS_MESSAGES;

        static {
            STATUS_MESSAGES = new HashMap<>();
            STATUS_MESSAGES.put(HttpStatus.SC_CREATED, "Document created successfully");
            STATUS_MESSAGES.put(HttpStatus.SC_BAD_REQUEST, "Bad request");
            STATUS_MESSAGES.put(HttpStatus.SC_UNAUTHORIZED, "Unauthorized. Please check your credentials.");
            STATUS_MESSAGES.put(HttpStatus.SC_FORBIDDEN, "Access forbidden");
            STATUS_MESSAGES.put(HttpStatus.SC_NOT_FOUND, "Resource not found");
            STATUS_MESSAGES.put(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Internal server error");
        }

        public static void handleStatusCode(int statusCode) {
            String message = STATUS_MESSAGES.getOrDefault(statusCode, "Unhandled status code: " + statusCode);
            System.out.println(message);
        }
    }

}

class SemaphoreRateLimiter implements RateLimiter {
    private final Semaphore semaphore;
    private final ArrayBlockingQueue<Long> queue;
    private final ScheduledExecutorService scheduler;
    private final Duration refillPeriod;

    public SemaphoreRateLimiter(int capacity, Duration refillPeriod) {
        this.semaphore = new Semaphore(capacity);
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.refillPeriod = refillPeriod;
        initScheduler();
    }

    public void initScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            while (!queue.isEmpty() && queue.peek() <= System.currentTimeMillis() - refillPeriod.toMillis()) {
                semaphore.release();
            }
        }, 1, 1, TimeUnit.SECONDS);

    }

    @Override
    public void acquire() {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        queue.offer(System.currentTimeMillis());
    }

}

interface RateLimiter {
    void acquire();
}


class DocumentSerializer implements ObjectSerializer {

    private final ObjectMapper objectMapper;

    public DocumentSerializer() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String serialize(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}

interface ObjectSerializer {
    String serialize(Object object);
}

class DocumentDTO {
    @JsonProperty("participant_inn")
    private String participantInn;
    @JsonProperty("doc_id")
    private String docId;
    @JsonProperty("doc_status")
    private String docStatus;
    @JsonProperty("doc_type")
    private String docType;
    @JsonProperty("import_request")
    private boolean importRequest;
    @JsonProperty("owner_inn")
    private String ownerInn;
    @JsonProperty("producer_inn")
    private String producerInn;
    @JsonProperty("production_date")
    private String productionDate;
    @JsonProperty("production_type")
    private String productionType;
    @JsonProperty("products")
    private List<ProductDTO> products;
    @JsonProperty("reg_date")
    private String regDate;
    @JsonProperty("reg_number")
    private String regNumber;

    private DocumentDTO(Builder builder) {
        this.participantInn = builder.participantInn;
        this.docId = builder.docId;
        this.docStatus = builder.docStatus;
        this.docType = builder.docType;
        this.importRequest = builder.importRequest;
        this.ownerInn = builder.ownerInn;
        this.producerInn = builder.producerInn;
        this.productionDate = builder.productionDate;
        this.productionType = builder.productionType;
        this.products = builder.products;
        this.regDate = builder.regDate;
        this.regNumber = builder.regNumber;
    }

    public static class Builder {
        private String participantInn;
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String productionType;
        private List<ProductDTO> products;
        private String regDate;
        private String regNumber;

        public Builder participantInn(String participantInn) {
            this.participantInn = participantInn;
            return this;
        }

        public Builder docId(String docId) {
            this.docId = docId;
            return this;
        }

        public Builder docStatus(String docStatus) {
            this.docStatus = docStatus;
            return this;
        }

        public Builder docType(String docType) {
            this.docType = docType;
            return this;
        }

        public Builder importRequest(boolean importRequest) {
            this.importRequest = importRequest;
            return this;
        }

        public Builder ownerInn(String ownerInn) {
            this.ownerInn = ownerInn;
            return this;
        }

        public Builder producerInn(String producerInn) {
            this.producerInn = producerInn;
            return this;
        }

        public Builder productionDate(String productionDate) {
            this.productionDate = productionDate;
            return this;
        }

        public Builder productionType(String productionType) {
            this.productionType = productionType;
            return this;
        }

        public Builder products(List<ProductDTO> products) {
            this.products = products;
            return this;
        }

        public Builder regDate(String regDate) {
            this.regDate = regDate;
            return this;
        }

        public Builder regNumber(String regNumber) {
            this.regNumber = regNumber;
            return this;
        }

        public DocumentDTO build() {
            return new DocumentDTO(this);
        }
    }
}

class ProductDTO {
    @JsonProperty("certificate_document")
    private String certificateDocument;
    @JsonProperty("certificate_document_date")
    private String certificateDocumentDate;
    @JsonProperty("certificate_document_number")
    private String certificateDocumentNumber;
    @JsonProperty("owner_inn")
    private String ownerInn;
    @JsonProperty("producer_inn")
    private String producerInn;
    @JsonProperty("production_date")
    private String productionDate;
    @JsonProperty("tnved_code")
    private String tnvedCode;
    @JsonProperty("uit_code")
    private String uitCode;
    @JsonProperty("uitu_code")
    private String uituCode;

    private ProductDTO(Builder builder) {
        this.certificateDocument = builder.certificateDocument;
        this.certificateDocumentDate = builder.certificateDocumentDate;
        this.certificateDocumentNumber = builder.certificateDocumentNumber;
        this.ownerInn = builder.ownerInn;
        this.producerInn = builder.producerInn;
        this.productionDate = builder.productionDate;
        this.tnvedCode = builder.tnvedCode;
        this.uitCode = builder.uitCode;
        this.uituCode = builder.uituCode;
    }

    public static class Builder {
        private String certificateDocument;
        private String certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;

        public Builder() {
        }

        public Builder certificateDocument(String certificateDocument) {
            this.certificateDocument = certificateDocument;
            return this;
        }

        public Builder certificateDocumentDate(String certificateDocumentDate) {
            this.certificateDocumentDate = certificateDocumentDate;
            return this;
        }

        public Builder certificateDocumentNumber(String certificateDocumentNumber) {
            this.certificateDocumentNumber = certificateDocumentNumber;
            return this;
        }

        public Builder ownerInn(String ownerInn) {
            this.ownerInn = ownerInn;
            return this;
        }

        public Builder producerInn(String producerInn) {
            this.producerInn = producerInn;
            return this;
        }

        public Builder productionDate(String productionDate) {
            this.productionDate = productionDate;
            return this;
        }

        public Builder tnvedCode(String tnvedCode) {
            this.tnvedCode = tnvedCode;
            return this;
        }

        public Builder uitCode(String uitCode) {
            this.uitCode = uitCode;
            return this;
        }

        public Builder uituCode(String uituCode) {
            this.uituCode = uituCode;
            return this;
        }

        public ProductDTO build() {
            return new ProductDTO(this);
        }
    }
}