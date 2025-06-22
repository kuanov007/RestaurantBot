package uz.app.entity;

import lombok.Builder;
import lombok.Data;

import java.util.Random;
import java.util.UUID;

@Builder
@Data
public class Food {
    private final UUID id = UUID.randomUUID();
    private String name;
    private String dish;
    private final Double price = Math.floor(new Random().nextDouble(0, 15) * 100) / 100; ;
    private int amount;
}
