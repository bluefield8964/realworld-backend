package realworld_backend.service;

import org.springframework.stereotype.Service;
import realworld_backend.model.Tag;
import realworld_backend.repository.TagRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TagService {

    private final TagRepository tagRepository;
    private  TagService ( TagRepository tagRepository){
        this.tagRepository=tagRepository;
    }
    public Set<Tag> buildTags(Set<String> tagNames){
        //find the tag already was joined into the table tags
        List<Tag> exitNames = tagRepository.findByNameIn(tagNames);
        //gain the set with name only
        Set<String> collect = exitNames.stream().map(Tag::getName).collect(Collectors.toSet());
        //filter the name didnt join before 
        Set<String> newNames = collect.stream().filter(name -> !collect.contains(name)).collect(Collectors.toSet());
        List<Tag> newList = newNames.stream().map(name -> tagRepository.save(new Tag(null, name))).toList();
        Set<Tag> exitTags = new HashSet<>(exitNames);
        exitTags.addAll(newList);
        return exitTags;
    }
}
