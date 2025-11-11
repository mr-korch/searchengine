package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "indexes")
public class IndexEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false) // FK колонка
    private PageEntity pageId;

    @ManyToOne
    @JoinColumn(name = "lemma_id", nullable = false) // FK колонка
    private LemmaEntity lemmaId;

    @Column(name = "rank_value", nullable = false)
    private Integer rankValue;
}
