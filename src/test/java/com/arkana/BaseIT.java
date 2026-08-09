package com.arkana;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public abstract class BaseIT {

    @Autowired
    protected EntityGeneratorService entityGeneratorService;
}
