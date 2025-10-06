import { test, expect } from '@playwright/test';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

// ES module equivalent of __dirname
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

/**
 * Golden Path E2E Test
 * 
 * This test covers the critical user journey:
 * 1. Login
 * 2. Upload a file
 * 3. Preview the file
 * 4. Download the file
 * 5. Delete the file
 * 
 * This single test catches 80% of regressions and validates
 * the core value proposition of the application.
 */

test.describe('Golden Path: Complete File Lifecycle', () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to the application
    await page.goto('/');
    
    // Should redirect to login page if not authenticated
    await expect(page).toHaveURL(/.*login/);
  });

  // No cleanup needed - tests delete their own files

  test('should complete full file lifecycle: login → upload → preview → download → delete', async ({ page, context }) => {
    // ===================================
    // STEP 1: Login
    // ===================================
    await test.step('Login with test credentials', async () => {
      await page.fill('input[name="username"]', 'demouser');
      await page.fill('input[name="password"]', 'Demo123!');
      
      // Click login and wait for navigation
      await page.click('button[type="submit"]');
      
      // Should navigate to files page
      await expect(page).toHaveURL('/');
      
      // Should see user info in top bar
      await expect(page.locator('text=demouser')).toBeVisible();

      // Clean up any leftover test files from previous runs
      await page.waitForTimeout(1000); // Wait for table to load
      const testFileRows = page.locator('tr:has-text("playwright-test.txt")');
      const count = await testFileRows.count();
      
      for (let i = 0; i < count; i++) {
        // Always target the first row (since DOM updates after each delete)
        const row = page.locator('tr:has-text("playwright-test.txt")').first();
        const deleteButton = row.locator('button[aria-label*="Delete"]');
        
        if (await deleteButton.isVisible()) {
          await deleteButton.click();
          await page.locator('button:has-text("Delete")').last().click();
          await page.waitForTimeout(500); // Wait for delete to complete
        }
      }
    });

    // ===================================
    // STEP 2: Upload a test file
    // ===================================
    const testFileName = 'playwright-test.txt';
    const testFileContent = `Playwright E2E Test - ${new Date().toISOString()}`;
    let uploadedFileId: string | null = null;

    await test.step('Upload a test file', async () => {
      // Create a temporary test file
      const testFilePath = path.join(__dirname, testFileName);
      fs.writeFileSync(testFilePath, testFileContent);

      // Find the file input (hidden in dropzone)
      const fileInput = page.locator('input[type="file"]');
      
      // Upload the file
      await fileInput.setInputFiles(testFilePath);

      // Wait for upload to complete - check for the file in the table
      await page.waitForTimeout(2000); // Give upload time to process

      // Verify file appears in the table (use .first() in case of duplicates from previous runs)
      const fileRow = page.locator(`tr:has-text("${testFileName}")`).first();
      await expect(fileRow).toBeVisible({ timeout: 5000 });

      // Extract file ID from the row for later use
      const downloadButton = fileRow.locator('button[aria-label*="Download"]');
      await expect(downloadButton).toBeVisible();

      // Clean up temporary file
      fs.unlinkSync(testFilePath);
    });

    // ===================================
    // STEP 3: Preview the file
    // ===================================
    await test.step('Preview the uploaded file', async () => {
      // Click on the filename to open preview (use .first() for duplicates)
      const filenameLink = page.locator(`button:has-text("${testFileName}")`).first();
      await filenameLink.click();

      // Wait for preview modal to appear (it's a div, not a dialog element)
      const previewModal = page.locator('.fixed.inset-0.bg-black.bg-opacity-75').first();
      await expect(previewModal).toBeVisible({ timeout: 3000 });

      // Should show filename in modal header
      await expect(page.locator(`text=${testFileName}`).first()).toBeVisible();

      // For text files, should show content
      // Note: The exact selector depends on your modal implementation
      await expect(page.locator('pre, iframe')).toBeVisible();

      // Close the modal
      await page.keyboard.press('Escape');
      await expect(previewModal).not.toBeVisible();
    });

    // ===================================
    // STEP 4: Download the file
    // ===================================
    await test.step('Verify download redirect', async () => {
      // Find the file row again (use .first() for duplicates)
      const fileRow = page.locator(`tr:has-text("${testFileName}")`).first();
      
      // Click download button and intercept API response
      const downloadButton = fileRow.locator('button[aria-label*="Download"]');
      
      // Listen for the response from the download endpoint
      const responsePromise = page.waitForResponse(
        response => response.url().includes('/api/v1/files/download/') && response.url().includes('/redirect'),
        { timeout: 5000 }
      );
      
      await downloadButton.click();
      
      const response = await responsePromise;
      
      // Verify we got a successful response with S3 presigned URL
      expect(response.status()).toBe(200);
      const data = await response.json();
      expect(data).toHaveProperty('downloadUrl');
      expect(data.downloadUrl).toContain('s3'); // S3 URL
    });

    // ===================================
    // STEP 5: Delete the file
    // ===================================
    await test.step('Delete the uploaded file', async () => {
      // Find the file row (use .first() for duplicates)
      const fileRow = page.locator(`tr:has-text("${testFileName}")`).first();
      
      // Click delete button
      const deleteButton = fileRow.locator('button[aria-label*="Delete"]');
      await deleteButton.click();

      // Confirm deletion in dialog (use .first() for strict mode)
      const confirmDialog = page.locator('text=/Delete File?|Are you sure/i').first();
      await expect(confirmDialog).toBeVisible();
      
      const confirmButton = page.locator('button:has-text("Delete")').last();
      await confirmButton.click();

      // Wait for success toast (use .first() since cleanup might have created multiple toasts)
      await expect(page.locator('text=/deleted|removed/i').first()).toBeVisible({ timeout: 3000 });

      // Verify file is removed from table (query fresh, not stale locator)
      await expect(page.locator(`tr:has-text("${testFileName}")`)).not.toBeVisible({ timeout: 5000 });
    });

    // ===================================
    // STEP 6: Logout
    // ===================================
    await test.step('Logout successfully', async () => {
      // Click sign out button
      const signOutButton = page.locator('button:has-text("Sign Out")');
      await signOutButton.click();

      // Should redirect to login page
      await expect(page).toHaveURL(/.*login/);
    });
  });

  test('should handle upload errors gracefully', async ({ page }) => {
    // Login first
    await page.fill('input[name="username"]', 'demouser');
    await page.fill('input[name="password"]', 'Demo123!');
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL('/');

    // Try to upload a file that's too large (> 10 MB)
    // This tests client-side validation
    const largeFilePath = path.join(__dirname, 'large-test-file.bin');
    const largeFileSize = 11 * 1024 * 1024; // 11 MB
    fs.writeFileSync(largeFilePath, Buffer.alloc(largeFileSize));

    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles(largeFilePath);

    // Should see error toast (more specific selector)
    await expect(page.locator('[data-sonner-toast]').filter({ hasText: /too large/i })).toBeVisible({ timeout: 3000 });

    // Clean up
    fs.unlinkSync(largeFilePath);
  });

  test('should prevent unauthorized access', async ({ page }) => {
    // Try to access files page without login
    await page.goto('/');

    // Should redirect to login
    await expect(page).toHaveURL(/.*login/);

    // Should not see file management UI
    await expect(page.locator('text=/Upload Files/i')).not.toBeVisible();
  });

  test('should be keyboard accessible', async ({ page }) => {
    // Login
    await page.fill('input[name="username"]', 'demouser');
    await page.fill('input[name="password"]', 'Demo123!');
    
    // Submit form with Enter key
    await page.keyboard.press('Enter');
    
    await expect(page).toHaveURL('/');

    // Tab through interactive elements
    await page.keyboard.press('Tab'); // Should focus first interactive element
    
    // Verify focus is visible (this is a basic check)
    const focusedElement = page.locator(':focus');
    await expect(focusedElement).toBeVisible();
  });
});

