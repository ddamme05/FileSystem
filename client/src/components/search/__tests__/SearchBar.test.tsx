import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { SearchBar } from '../SearchBar';

describe('SearchBar', () => {
  it('renders with placeholder', () => {
    render(<SearchBar onSearch={vi.fn()} placeholder="Test placeholder" />);
    
    const input = screen.getByPlaceholderText('Test placeholder');
    expect(input).toBeInTheDocument();
  });

  it('calls onSearch when form is submitted', () => {
    const handleSearch = vi.fn();
    render(<SearchBar onSearch={handleSearch} />);
    
    const input = screen.getByPlaceholderText('Search files...');
    const form = input.closest('form')!;
    
    fireEvent.change(input, { target: { value: 'machine learning' } });
    fireEvent.submit(form);
    
    expect(handleSearch).toHaveBeenCalledWith('machine learning');
  });

  it('trims whitespace from query', () => {
    const handleSearch = vi.fn();
    render(<SearchBar onSearch={handleSearch} />);
    
    const input = screen.getByPlaceholderText('Search files...');
    const form = input.closest('form')!;
    
    fireEvent.change(input, { target: { value: '  test query  ' } });
    fireEvent.submit(form);
    
    expect(handleSearch).toHaveBeenCalledWith('test query');
  });

  it('does not call onSearch for empty query', () => {
    const handleSearch = vi.fn();
    render(<SearchBar onSearch={handleSearch} />);
    
    const form = screen.getByPlaceholderText('Search files...').closest('form')!;
    
    fireEvent.submit(form);
    
    expect(handleSearch).not.toHaveBeenCalled();
  });

  it('clears query when clear button is clicked', () => {
    const handleSearch = vi.fn();
    render(<SearchBar onSearch={handleSearch} />);
    
    const input = screen.getByPlaceholderText('Search files...') as HTMLInputElement;
    
    // Type a query
    fireEvent.change(input, { target: { value: 'test' } });
    expect(input.value).toBe('test');
    
    // Click clear button
    const clearButton = screen.getByText('×');
    fireEvent.click(clearButton);
    
    expect(input.value).toBe('');
    expect(handleSearch).toHaveBeenCalledWith('');
  });

  it('uses initial query if provided', () => {
    render(<SearchBar onSearch={vi.fn()} initialQuery="initial search" />);
    
    const input = screen.getByPlaceholderText('Search files...') as HTMLInputElement;
    expect(input.value).toBe('initial search');
  });
});



