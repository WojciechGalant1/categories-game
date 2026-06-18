interface LeaveRoomButtonProps {
    onLeave: () => void;
    className?: string;
}

export const LeaveRoomButton = ({ onLeave, className = "" }: LeaveRoomButtonProps) => {
    return (
        <button
            type="button"
            onClick={onLeave}
            className={`px-4 py-2 rounded-xl text-sm font-medium border border-brand-border text-gray-300 hover:text-white hover:border-gray-500 transition-colors ${className}`}
        >
            Opuść pokój
        </button>
    );
};
