package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Objects;

@Getter
@Setter
@Entity
//@Table(name = "pages",
//        indexes = {
//                @Index(name = "idx_page_path", columnList = "path")
//        })
@Table(name = "pages")
public class PageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false) // FK колонка
    private SiteEntity siteId;

    @Column(name = "path", columnDefinition = "VARCHAR(255)")
    private String path;

    @Column(nullable = false)
    private Integer code;

    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;
}
