package com.stockroom.wms.config;

import com.stockroom.wms.domain.*;
import com.stockroom.wms.dto.stock.StockDTOs.ReceiveStockRequest;
import com.stockroom.wms.repository.*;
import com.stockroom.wms.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@Profile("dev")
@Slf4j
public class DevDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final LocationRepository locationRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;
    private final StockService stockService;

    public DevDataInitializer(
            UserRepository userRepository,
            WarehouseRepository warehouseRepository,
            LocationRepository locationRepository,
            ProductRepository productRepository,
            PasswordEncoder passwordEncoder,
            StockService stockService
    ) {
        this.userRepository = userRepository;
        this.warehouseRepository = warehouseRepository;
        this.locationRepository = locationRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
        this.stockService = stockService;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Populating Nairobi FMCG demonstration seed data for DEV environment...");

        // 1. Users
        String encodedPass = passwordEncoder.encode("Password123!");
        User admin = userRepository.save(User.builder().username("admin").password(encodedPass).fullName("System Administrator").email("admin@stockroom.co.ke").role(Role.ADMIN).enabled(true).build());
        User manager = userRepository.save(User.builder().username("manager").password(encodedPass).fullName("Amina Odhiambo").email("amina.odhiambo@stockroom.co.ke").role(Role.WAREHOUSE_MANAGER).enabled(true).build());
        User picker = userRepository.save(User.builder().username("picker").password(encodedPass).fullName("John Kamau").email("john.kamau@stockroom.co.ke").role(Role.PICKER).enabled(true).build());
        User auditor = userRepository.save(User.builder().username("auditor").password(encodedPass).fullName("Grace Mutua").email("grace.mutua@stockroom.co.ke").role(Role.AUDITOR).enabled(true).build());

        // 2. Warehouses
        Warehouse wh1 = warehouseRepository.save(Warehouse.builder().code("NBO-01").name("Nairobi Central Depot").county("Nairobi").town("Industrial Area").address("Commercial Street, Gate 4").managerId(manager.getId()).active(true).build());
        Warehouse wh2 = warehouseRepository.save(Warehouse.builder().code("NBO-02").name("Embakasi Transit Hub").county("Nairobi").town("Embakasi").address("Airport North Road, Plot 12").managerId(manager.getId()).active(true).build());
        Warehouse wh3 = warehouseRepository.save(Warehouse.builder().code("NBO-03").name("Baba Dogo Distribution").county("Nairobi").town("Ruaraka").address("Baba Dogo Road, Shed B").managerId(manager.getId()).active(true).build());

        // 3. Locations
        Location locWh1Rec = locationRepository.save(Location.builder().warehouse(wh1).code("REC-01").zone(Zone.RECEIVING).capacityUnits(5000).build());
        Location locWh1A01 = locationRepository.save(Location.builder().warehouse(wh1).code("A-01-01").zone(Zone.STORAGE).capacityUnits(1500).build());
        Location locWh1A02 = locationRepository.save(Location.builder().warehouse(wh1).code("A-01-02").zone(Zone.STORAGE).capacityUnits(1500).build());
        Location locWh1Pick = locationRepository.save(Location.builder().warehouse(wh1).code("PCK-01").zone(Zone.PICKING).capacityUnits(800).build());
        Location locWh1Ret = locationRepository.save(Location.builder().warehouse(wh1).code("RET-01").zone(Zone.RETURNS).capacityUnits(500).build());

        Location locWh2Rec = locationRepository.save(Location.builder().warehouse(wh2).code("REC-01").zone(Zone.RECEIVING).capacityUnits(4000).build());
        Location locWh2A01 = locationRepository.save(Location.builder().warehouse(wh2).code("A-01-01").zone(Zone.STORAGE).capacityUnits(2000).build());
        Location locWh2Pick = locationRepository.save(Location.builder().warehouse(wh2).code("PCK-01").zone(Zone.PICKING).capacityUnits(1000).build());

        // 4. Products
        Product p1 = productRepository.save(Product.builder().sku("KEP-PEM-2KG").name("Pembe Maize Meal 2kg").description("Premium sifted maize flour 2kg bundle").category("Dry Goods").unitOfMeasure("BALE").barcode("06161101234566").reorderPoint(50).reorderQty(200).unitCost(new BigDecimal("185.00")).unitPrice(new BigDecimal("210.00")).build());
        Product p2 = productRepository.save(Product.builder().sku("KEP-JOG-2KG").name("Jogoo Maize Meal 2kg").description("Fortified sifted maize meal 2kg pack").category("Dry Goods").unitOfMeasure("BALE").barcode("06161102345674").reorderPoint(40).reorderQty(150).unitCost(new BigDecimal("190.00")).unitPrice(new BigDecimal("215.00")).build());
        Product p3 = productRepository.save(Product.builder().sku("KAP-RIN-5LT").name("Rina Pure Vegetable Oil 5L").description("Refined cooking palm olein 5-litre jerrican").category("Oils & Fats").unitOfMeasure("CARTON").barcode("06161103456782").reorderPoint(25).reorderQty(100).unitCost(new BigDecimal("1250.00")).unitPrice(new BigDecimal("1420.00")).build());
        Product p4 = productRepository.save(Product.builder().sku("KET-TEA-100").name("Ketepa Chai Premium Tea Bags 100s").description("Highland blend Kenyan black tea tagless bags").category("Beverages").unitOfMeasure("CARTON").barcode("06161104567899").reorderPoint(30).reorderQty(80).unitCost(new BigDecimal("275.00")).unitPrice(new BigDecimal("330.00")).build());
        Product p5 = productRepository.save(Product.builder().sku("BRK-MLK-500").name("Brookside Fresh Milk 500ml").description("Pasteurised whole cow milk 500ml pouch").category("Dairy").unitOfMeasure("CRATE").barcode("06161105678906").reorderPoint(60).reorderQty(250).unitCost(new BigDecimal("55.00")).unitPrice(new BigDecimal("68.00")).build());
        Product p6 = productRepository.save(Product.builder().sku("UNI-OMO-1KG").name("Omo Hand Washing Powder 1kg").description("Active foam laundry detergent with scent").category("Cleaning").unitOfMeasure("CARTON").barcode("06161106789013").reorderPoint(20).reorderQty(60).unitCost(new BigDecimal("260.00")).unitPrice(new BigDecimal("310.00")).build());

        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "admin", null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
                );
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            // 5. Inbound Stock Receipts
            stockService.receiveStock(ReceiveStockRequest.builder().productId(p1.getId()).locationId(locWh1A01.getId()).quantity(150).batchNumber("BAT-PEM-2026-01").expiryDate(LocalDate.now().plusMonths(8)).reference("GRN-2026-001").build(), "admin");
            stockService.receiveStock(ReceiveStockRequest.builder().productId(p1.getId()).locationId(locWh1Pick.getId()).quantity(50).batchNumber("BAT-PEM-2026-02").expiryDate(LocalDate.now().plusMonths(8)).reference("GRN-2026-002").build(), "admin");
            stockService.receiveStock(ReceiveStockRequest.builder().productId(p2.getId()).locationId(locWh1A02.getId()).quantity(120).batchNumber("BAT-JOG-2026-01").expiryDate(LocalDate.now().plusMonths(7)).reference("GRN-2026-003").build(), "admin");
            stockService.receiveStock(ReceiveStockRequest.builder().productId(p3.getId()).locationId(locWh1A01.getId()).quantity(80).batchNumber("BAT-RIN-2026-01").expiryDate(LocalDate.now().plusYears(1)).reference("GRN-2026-004").build(), "admin");
            stockService.receiveStock(ReceiveStockRequest.builder().productId(p4.getId()).locationId(locWh2A01.getId()).quantity(60).batchNumber("BAT-KET-2026-01").expiryDate(LocalDate.now().plusYears(2)).reference("GRN-2026-005").build(), "admin");

            log.info("Dev seed data loaded successfully! 4 users, 3 warehouses, 8 locations, 6 products, 5 stock batches.");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
