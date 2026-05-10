import { Logo } from './Logo';
import { Navbar } from './Navbar';

export const Header = () => {
    return (
        <header className="fixed top-0 left-0 w-full z-50 px-4 py-4 sm:px-6 sm:py-6 animate-fade-in-up">
            <div className="max-w-6xl mx-auto glass-panel px-5 py-3 flex items-center justify-between rounded-full">
                <Logo />
                <Navbar />
                
                {/* Mobile Menu Button - visible only on small screens */}
                <button className="md:hidden p-2 text-gray-300 hover:text-white bg-brand-surface rounded-full border border-brand-border">
                    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-6 h-6">
                        <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5" />
                    </svg>
                </button>
            </div>
        </header>
    );
};
