import { Link } from 'react-router-dom';

export const Navbar = () => {
    const navItems = [
        { label: "Zasady", href: "#" },
        { label: "Pokoje", href: "#" },
    ];

    return (
        <nav className="hidden md:flex items-center gap-8">
            {navItems.map((item, idx) => (
                <a
                    key={idx}
                    href={item.href}
                    className="text-gray-300 font-medium hover:text-white transition-colors duration-300 relative group"
                >
                    {item.label}
                    <span className="absolute left-0 -bottom-1 w-0 h-0.5 bg-brand-accent transition-all duration-300"></span>
                </a>
            ))}
        </nav>
    );
};
