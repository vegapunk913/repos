module com.example.exam_java {
    requires java.desktop;
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires jakarta.persistence;
    requires org.hibernate.orm.core;
    requires jbcrypt;

    opens com.example.exam_java to javafx.fxml;
    opens com.example.exam_java.entity to javafx.base, org.hibernate.orm.core;
    opens com.example.exam_java.model to javafx.base;
    exports com.example.exam_java;
    exports com.example.exam_java.server;
}
