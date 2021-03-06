package id.veintechnology.apps.library.id.veintechnology.apps.service.book;

import id.veintechnology.apps.library.id.veintechnology.apps.dao.Book;
import id.veintechnology.apps.library.id.veintechnology.apps.dao.Category;
import id.veintechnology.apps.library.id.veintechnology.apps.repository.BookRepository;
import id.veintechnology.apps.library.id.veintechnology.apps.service.book.exception.DuplicateBookCodeException;
import id.veintechnology.apps.library.id.veintechnology.apps.service.book.exception.BookNotFoundException;
import id.veintechnology.apps.library.id.veintechnology.apps.service.category.CategoryService;
import id.veintechnology.apps.library.id.veintechnology.apps.service.storage.StorageService;
import id.veintechnology.apps.library.id.veintechnology.apps.service.storage.exception.StorageErrorException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class DbBookService implements BookService {

    private final BookRepository bookRepository;
    private final CategoryService categoryService;
    private final StorageService storageService;

    @Autowired
    public DbBookService(BookRepository bookRepository, CategoryService categoryService, StorageService storageService) {
        this.bookRepository = bookRepository;
        this.categoryService = categoryService;
        this.storageService = storageService;
    }

    @Override
    public Book createNewBook(Book book, Set<String> categoryCodes) throws RuntimeException {
        if(categoryCodes.size() > 0){
            Set<Category> categories = categoryService.findByCategoryCodes(categoryCodes);
            if(categories.size() != categoryCodes.size()){
                throw new DataIntegrityViolationException("Failed to insert new book, some category doesn't exist.");
            }
        }
        Book existBook = bookRepository.findFirstByCode(book.getCode());
        if(existBook != null){
            throw new DuplicateBookCodeException(existBook.getCode());
        }
        existBook = bookRepository.save(book);
        existBook = fillCoverImage(existBook);
        return existBook;
    }

    @Override
    public Book removeAllCategories(Book book) {
        book.getCategories().removeAll(book.getCategories());
        // Book updatedBook = bookRepository.saveAndFlush(book);
        // updatedBook.setCoverImage(storageService.composeImageUrl(updatedBook.getCoverImage()));
        // return updatedBook;
        return bookRepository.saveAndFlush(book);
    }

    @Override
    public Book findByCode(String code) {
        return bookRepository.findFirstByCode(code);
    }

    @Override
    public Book findById(Long bookId) {
        Optional<Book> existBook = bookRepository.findById(bookId);
        return existBook.orElse(null);
    }

    @Override
    public List<Book> findByCodes(List<String> codes) {
        List<Book> books = bookRepository.findByCodeIn(codes);
        return books.stream().peek(this::fillCoverImage).collect(Collectors.toList());
    }

    @Override
    public Page<Book> retrieveBook(int size, int page) {
        Sort sort = new Sort(Sort.Direction.ASC, "title");
        Pageable pageRequest = new PageRequest(page, size, sort);
        return bookRepository.findAll(pageRequest);
    }

    @Override
    public Book updateBook(Book book, Set<String> categoryCodes) {
        Set<Category> categories = new HashSet<>();
        // check existence of categories by categoryCodes
        if(categoryCodes.size() > 0){
            categories = categoryService.findByCategoryCodes(categoryCodes);
            if(categories.size() != categoryCodes.size()){
                throw new DataIntegrityViolationException("Failed to insert new book, some category doesn't exist.");
            }
        }

        // delete old relation by categories
        book = removeAllCategories(book);

        // attach new many2many relation
        if(categories.size() > 0){
            book.setCategories(categories);
        }

        Book updatedBook = bookRepository.save(book);
        updatedBook = fillCoverImage(updatedBook);
        return updatedBook;
    }

    @Override
    public Book uploadCover(String code, MultipartFile file) throws BookNotFoundException, StorageErrorException{
        // get existing book
        Book book = findByCode(code);
        if(book == null){
            throw new BookNotFoundException(code);
        }

        // upload file
        String rootPath = storageService.getRootPath().toString();
        Path path = storageService.store(file, "/", code, true);
        String pathImage = path.toString().replace(rootPath, "");
        book.setCoverImage(pathImage);
        return bookRepository.save(book);
    }

    @Override
    public Book borrowBook(Book book) {
        book.setStock(book.getStock() - 1);
        Book savedBook = bookRepository.save(book);
        savedBook = fillCoverImage(savedBook);
        return savedBook;
    }

    @Override
    public void updateStockBooks(Book book, int quantity) {
        bookRepository.updateStock(book.getId(), quantity);
    }

    @Override
    public void addStockByBookId(Long bookId, int quantity) {
        bookRepository.addStock(bookId, quantity);
    }

    @Override
    public void subtractStockByBookId(Long bookId, int quantity) {
        bookRepository.subtractStock(bookId, quantity);
    }

    @Override
    public Book destroyBook(Book book) {
        bookRepository.delete(book);
        return book;
    }

    @Override
    public Book fillCoverImage(Book book) {
        if(book.getCoverImage() == null){
            book.setCoverImage(storageService.composeStaticUrl("/no_image.jpg"));
            return book;
        }
        book.setCoverImage(storageService.composeImageUrl(book.getCoverImage()));
        return book;
    }

    @Override
    public Page<Book> searchBook(String query, int size, int page) {
        Pageable pageRequest = new PageRequest(page, size);
        return bookRepository.searchBookByTitle(query.toLowerCase(), pageRequest);
    }

    @Override
    public List<Book> validateBook(List<String> codes) throws BookNotFoundException {
        Set<Book> existBook = bookRepository.findByCodes(codes);
        Set<String> existBookCode = existBook.stream().map(Book::getCode).collect(Collectors.toSet());
        Set<String> setQueryCodes = new HashSet<>(codes);
        setQueryCodes.removeAll(existBookCode);
        if(setQueryCodes.size() > 0){
            String unknownCodes = String.join(",", setQueryCodes);
            throw new BookNotFoundException(unknownCodes);
        }
        return new ArrayList<>(existBook);
    }

    @Override
    public Page<Book> fetchBookByCategory(String categoryCode, int size, int page) {
        Pageable pageable = new PageRequest(page, size);
        return bookRepository.findByCategoryCode(categoryCode, pageable);
    }
}
