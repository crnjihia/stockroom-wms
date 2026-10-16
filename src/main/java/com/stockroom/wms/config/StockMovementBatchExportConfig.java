package com.stockroom.wms.config;

import com.stockroom.wms.domain.StockMovement;
import com.stockroom.wms.repository.StockMovementRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.support.JobRegistryBeanPostProcessor;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class StockMovementBatchExportConfig {

    public static final int CHUNK_SIZE = 1000;
    public static final String JOB_NAME = "stockMovementExportJob";
    public static final String STEP_NAME = "stockMovementExportStep";


    @Bean
    public RepositoryItemReader<StockMovement> stockMovementItemReader(StockMovementRepository repository) {
        Map<String, Sort.Direction> sorts = new HashMap<>();
        sorts.put("id", Sort.Direction.ASC);

        return new RepositoryItemReaderBuilder<StockMovement>()
                .name("stockMovementItemReader")
                .repository(repository)
                .methodName("findAll")
                .sorts(sorts)
                .pageSize(CHUNK_SIZE)
                .build();
    }

    @Bean
    public ItemProcessor<StockMovement, String> stockMovementItemProcessor() {
        return movement -> String.format(
                "%d,%s,%s,%s,%d,%s,%s,%s,%s,%s",
                movement.getId(),
                movement.getProduct() != null ? movement.getProduct().getSku() : "",
                movement.getLocation() != null ? movement.getLocation().getCode() : "",
                movement.getType() != null ? movement.getType().name() : "",
                movement.getQuantity() != null ? movement.getQuantity() : 0,
                movement.getReferenceType() != null ? movement.getReferenceType() : "",
                movement.getReferenceId() != null ? movement.getReferenceId() : "",
                movement.getUserId() != null ? movement.getUserId() : "",
                movement.getCreatedAt() != null ? movement.getCreatedAt().toString() : "",
                movement.getNotes() != null ? movement.getNotes().replace(",", ";").replace("\n", " ") : ""
        );
    }

    @Bean
    public ItemWriter<String> stockMovementItemWriter() {
        return chunk -> {
            File exportFile = new File("./target/reports/stock_movements_batch_export.csv");
            if (exportFile.getParentFile() != null) {
                exportFile.getParentFile().mkdirs();
            }
            boolean isNew = !exportFile.exists() || exportFile.length() == 0;
            try (FileWriter fw = new FileWriter(exportFile, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                if (isNew) {
                    pw.println("id,sku,location,type,quantity,referenceType,referenceId,userId,createdAt,notes");
                }
                for (String line : chunk) {
                    pw.println(line);
                }
            }
        };
    }

    @Bean
    public Step stockMovementExportStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            RepositoryItemReader<StockMovement> stockMovementItemReader,
            ItemProcessor<StockMovement, String> stockMovementItemProcessor,
            ItemWriter<String> stockMovementItemWriter
    ) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<StockMovement, String>chunk(CHUNK_SIZE, transactionManager)
                .reader(stockMovementItemReader)
                .processor(stockMovementItemProcessor)
                .writer(stockMovementItemWriter)
                .build();
    }

    @Bean
    public Job stockMovementExportJob(JobRepository jobRepository, Step stockMovementExportStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(stockMovementExportStep)
                .build();
    }
}
